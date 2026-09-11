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

/**
 * OpenAI uyumlu herhangi bir API ile doğrudan konuşur:
 * xAI (Grok), OpenAI, OpenRouter, Groq, Together, vLLM, LM Studio, llama.cpp server...
 *
 * Bu modda araçlar (tool) çalışmaz; sunucu tarafı yetenekler için Hermes Agent gerekir.
 */
class OpenAiBackend(
    override val profile: ServerProfile,
    private val http: HttpTransport,
) : Backend {

    private fun headers(): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        if (profile.token.isNotBlank()) put("Authorization", "Bearer ${profile.token}")
    }

    override suspend fun health(): AgentInfo {
        val list = models()
        return AgentInfo(
            ok = true,
            name = Urls.hostOf(profile.baseUrl) ?: "OpenAI uyumlu API",
            version = "openai-api",
            backends = listOf("openai"),
        ).copy(bots = list.size)
    }

    override suspend fun models(): List<String> {
        val body = http.request(Urls.openAiModelsUrl(profile.baseUrl), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return emptyList()
        val data = root["data"].arr() ?: root["models"].arr() ?: return emptyList()
        return data.mapNotNull { el ->
            el.obj()?.str("id")?.ifBlank { null } ?: el.obj()?.str("name")?.ifBlank { null } ?: el.text()
        }.sorted()
    }

    override fun run(bot: BotSpec, history: List<ChatMessage>, input: String): Flow<RunEvent> = flow {
        if (bot.model.isBlank()) {
            emit(RunEvent.Failed("Bu bot için model adı boş. Bot ayarlarından bir model seçin (ör. grok-3, gpt-4o-mini)."))
            emit(RunEvent.Finished("error"))
            return@flow
        }
        val messages = Prompts.build(bot, history, input)
        val payload = buildJsonObject {
            put("model", bot.model)
            put("stream", true)
            put("temperature", bot.temperature)
            if (bot.maxTokens > 0) put("max_tokens", bot.maxTokens)
            putJsonArray("messages") {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    }
                }
            }
        }.toString()

        val started = System.currentTimeMillis()
        emit(RunEvent.Started("local-${started}"))
        var accumulated = StringBuilder()
        var finishedEmitted = false

        http.streamSse(Urls.openAiChatUrl(profile.baseUrl), "POST", headers(), payload).collect { sse ->
            if (sse.isDone) return@collect
            val obj = runCatching { HermesJson.parseToJsonElement(sse.data).obj() }.getOrNull() ?: return@collect

            obj["error"]?.let { err ->
                val msg = err.obj()?.str("message") ?: err.text() ?: "API hatası"
                emit(RunEvent.Failed(msg))
                return@collect
            }

            val choice = obj["choices"].arr()?.firstOrNull()?.obj()
            val delta = choice?.path("delta", "content").text()
                ?: choice?.path("message", "content").text()
            if (!delta.isNullOrEmpty()) {
                accumulated.append(delta)
                emit(RunEvent.Delta(delta))
            }
            obj["usage"].obj()?.let { u ->
                emit(RunEvent.Usage(u.int("prompt_tokens"), u.int("completion_tokens")))
            }
            val finish = choice?.str("finish_reason").orEmpty()
            if (finish.isNotEmpty() && finish != "null" && !finishedEmitted) {
                finishedEmitted = true
                if (accumulated.isNotEmpty()) emit(RunEvent.Message(accumulated.toString()))
                emit(RunEvent.Finished(finish, System.currentTimeMillis() - started))
            }
        }
        if (!finishedEmitted) {
            if (accumulated.isNotEmpty()) emit(RunEvent.Message(accumulated.toString()))
            emit(RunEvent.Finished("ok", System.currentTimeMillis() - started))
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(RunEvent.Failed(HttpTransport.friendlyError(e)))
        emit(RunEvent.Finished("error"))
    }
}
