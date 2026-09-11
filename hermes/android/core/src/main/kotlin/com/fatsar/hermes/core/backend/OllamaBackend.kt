package com.fatsar.hermes.core.backend

import com.fatsar.hermes.core.logic.Prompts
import com.fatsar.hermes.core.logic.Urls
import com.fatsar.hermes.core.model.AgentInfo
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HermesJson
import com.fatsar.hermes.core.net.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Ollama sunucusu (VPS veya ev bilgisayarı) ile doğrudan konuşur.
 * Yanıtlar NDJSON (her satır bir JSON) olarak akar.
 */
class OllamaBackend(
    override val profile: ServerProfile,
    private val http: HttpTransport,
) : Backend {

    private fun headers(): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        if (profile.token.isNotBlank()) put("Authorization", "Bearer ${profile.token}")
    }

    override suspend fun health(): AgentInfo {
        val list = models()
        return AgentInfo(
            ok = true,
            name = "Ollama @ ${Urls.hostOf(profile.baseUrl).orEmpty()}",
            version = "ollama",
            backends = listOf("ollama"),
            bots = list.size,
        )
    }

    override suspend fun models(): List<String> {
        val body = http.request(Urls.ollamaUrl(profile.baseUrl, "/api/tags"), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return emptyList()
        return root["models"].arr()?.mapNotNull { it.obj()?.str("name")?.ifBlank { null } }?.sorted() ?: emptyList()
    }

    override fun run(bot: BotSpec, history: List<ChatMessage>, input: String): Flow<RunEvent> = flow {
        if (bot.model.isBlank()) {
            emit(RunEvent.Failed("Bu bot için model adı boş. Ollama'daki bir model yazın (ör. llama3.1:8b, hermes3:8b)."))
            emit(RunEvent.Finished("error"))
            return@flow
        }
        val messages = Prompts.build(bot, history, input)
        val payload = buildJsonObject {
            put("model", bot.model)
            put("stream", true)
            putJsonArray("messages") {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    }
                }
            }
            putJsonObject("options") {
                put("temperature", bot.temperature)
                if (bot.maxTokens > 0) put("num_predict", bot.maxTokens)
            }
        }.toString()

        val started = System.currentTimeMillis()
        emit(RunEvent.Started("local-$started"))
        val accumulated = StringBuilder()

        http.streamLines(Urls.ollamaUrl(profile.baseUrl, "/api/chat"), "POST", headers(), payload).collect { line ->
            if (line.isBlank()) return@collect
            val obj = runCatching { HermesJson.parseToJsonElement(line).obj() }.getOrNull() ?: return@collect
            obj["error"]?.let { err ->
                emit(RunEvent.Failed(err.text() ?: "Ollama hatası"))
                return@collect
            }
            val piece = obj.path("message", "content").text().orEmpty()
            if (piece.isNotEmpty()) {
                accumulated.append(piece)
                emit(RunEvent.Delta(piece))
            }
            if (obj.bool("done")) {
                val pt = obj.int("prompt_eval_count")
                val ct = obj.int("eval_count")
                if (pt > 0 || ct > 0) emit(RunEvent.Usage(pt, ct))
                if (accumulated.isNotEmpty()) emit(RunEvent.Message(accumulated.toString()))
                emit(RunEvent.Finished("ok", System.currentTimeMillis() - started))
            }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(RunEvent.Failed(HttpTransport.friendlyError(e)))
        emit(RunEvent.Finished("error"))
    }
}
