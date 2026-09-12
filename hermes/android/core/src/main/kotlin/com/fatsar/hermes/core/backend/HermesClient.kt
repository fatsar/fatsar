package com.fatsar.hermes.core.backend

import com.fatsar.hermes.core.logic.Prompts
import com.fatsar.hermes.core.logic.Urls
import com.fatsar.hermes.core.model.AgentInfo
import com.fatsar.hermes.core.model.BackendInfo
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.RunDetail
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.RunSummary
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HermesJson
import com.fatsar.hermes.core.net.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Uygulamanın tek istemcisi: kendi sunucunuzda (VPS / ev bilgisayarı) çalışan
 * Hermes Agent. Model sağlayıcıları ve anahtarları agent'ta tanımlıdır;
 * uygulama yalnızca agent'ın hazır bildirdiği sağlayıcılar arasından seçim yapar.
 */
class HermesClient(
    val profile: ServerProfile,
    private val http: HttpTransport,
) {

    private fun headers(): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        if (profile.token.isNotBlank()) put("Authorization", "Bearer ${profile.token}")
    }

    private fun url(path: String) = Urls.hermesUrl(profile.baseUrl, path)

    suspend fun health(): AgentInfo {
        val body = http.request(url("/v1/health"), "GET", headers())
        return runCatching { HermesJson.decodeFromString(AgentInfo.serializer(), body) }
            .getOrElse { AgentInfo(ok = true, name = "hermes-agent") }
    }

    /** Agent'ta tanımlı LLM sağlayıcıları (hangileri kullanıma hazır bilgisiyle). */
    suspend fun backends(): List<BackendInfo> {
        val body = http.request(url("/v1/backends"), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return emptyList()
        val arr = root["backends"] ?: return emptyList()
        return runCatching {
            HermesJson.decodeFromJsonElement(ListSerializer(BackendInfo.serializer()), arr)
        }.getOrElse { emptyList() }
    }

    /** Seçilen sağlayıcının agent üzerinden görünen modelleri. */
    suspend fun models(backend: String): List<String> {
        val query = if (backend.isBlank()) "" else "?backend=" + Urls.encodeQuery(backend)
        val body = http.request(url("/v1/models$query"), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return emptyList()
        return root["models"].arr()?.mapNotNull { it.text() } ?: emptyList()
    }

    suspend fun listBots(): List<BotSpec> {
        val body = http.request(url("/v1/bots"), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return emptyList()
        val arr = root["bots"] ?: return emptyList()
        return runCatching {
            HermesJson.decodeFromJsonElement(ListSerializer(BotSpec.serializer()), arr)
        }.getOrElse { emptyList() }
    }

    suspend fun pushBot(bot: BotSpec) {
        val body = HermesJson.encodeToString(BotSpec.serializer(), bot)
        http.request(url("/v1/bots/${bot.id}"), "PUT", headers(), body)
    }

    suspend fun deleteBot(botId: String) {
        http.request(url("/v1/bots/$botId"), "DELETE", headers())
    }

    suspend fun runHistory(botId: String, limit: Int = 20): List<RunSummary> {
        val body = http.request(url("/v1/bots/$botId/runs?limit=$limit"), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return emptyList()
        val arr = root["runs"] ?: return emptyList()
        return runCatching {
            HermesJson.decodeFromJsonElement(ListSerializer(RunSummary.serializer()), arr)
        }.getOrElse { emptyList() }
    }

    /** Tek bir çalışmanın tam çıktısı (zamanlanmış sonuçları telefona getirmek için). */
    suspend fun runDetail(runId: String): RunDetail? {
        val body = http.request(url("/v1/runs/$runId"), "GET", headers())
        val root = runCatching { HermesJson.parseToJsonElement(body).obj() }.getOrNull() ?: return null
        val run = root["run"] ?: return null
        return runCatching { HermesJson.decodeFromJsonElement(RunDetail.serializer(), run) }.getOrNull()
    }

    suspend fun clearMessages(botId: String) {
        runCatching { http.request(url("/v1/bots/$botId/messages"), "DELETE", headers()) }
    }

    suspend fun cancel(runId: String) {
        runCatching { http.request(url("/v1/runs/$runId/cancel"), "POST", headers(), "{}") }
    }

    /** Botu agent üzerinde çalıştırır ve olayları akıtır. Akış istisna fırlatmaz. */
    fun run(bot: BotSpec, history: List<ChatMessage>, input: String): Flow<RunEvent> = flow {
        val payload = buildJsonObject {
            put("input", input)
            put("stream", true)
            put("session", "default")
            // Bot tanımı istekle birlikte gider: sunucuda kayıtlı değilse otomatik kaydedilir.
            put("bot", HermesJson.encodeToJsonElement(BotSpec.serializer(), bot))
            putJsonArray("history") {
                Prompts.history(bot, history).forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    }
                }
            }
            putJsonArray("tools") { bot.tools.forEach { add(it) } }
        }.toString()

        http.streamSse(url("/v1/bots/${bot.id}/runs"), "POST", headers(), payload).collect { sse ->
            if (sse.isDone) return@collect
            mapEvent(sse.event, sse.data)?.let { emit(it) }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(RunEvent.Failed(HttpTransport.friendlyError(e)))
        emit(RunEvent.Finished("error"))
    }

    private fun mapEvent(name: String, data: String): RunEvent? {
        val obj: JsonObject = runCatching { HermesJson.parseToJsonElement(data).obj() }.getOrNull()
            ?: return if (name == "token") RunEvent.Delta(data) else null
        return when (name) {
            "run.started" -> RunEvent.Started(obj.str("run_id"))
            "token", "delta" -> RunEvent.Delta(obj.str("text"))
            "tool.call" -> RunEvent.ToolCall(
                name = obj.str("name"),
                args = obj["args"]?.toString().orEmpty(),
                callId = obj.str("call_id"),
            )
            "tool.result" -> RunEvent.ToolResult(
                name = obj.str("name"),
                result = obj.str("result", obj["result"]?.toString().orEmpty()),
                ok = obj.bool("ok", true),
                callId = obj.str("call_id"),
            )
            "message" -> RunEvent.Message(obj.str("content"))
            "usage" -> RunEvent.Usage(obj.int("prompt_tokens"), obj.int("completion_tokens"))
            "run.finished" -> RunEvent.Finished(obj.str("status", "ok"), obj.long("duration_ms"))
            "error" -> RunEvent.Failed(obj.str("message", "Sunucu hatası"))
            "log" -> RunEvent.Log(obj.str("line"))
            else -> null
        }
    }
}
