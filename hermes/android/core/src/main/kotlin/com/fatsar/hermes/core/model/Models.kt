package com.fatsar.hermes.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bağlanılabilecek uç nokta türleri. */
enum class ServerKind {
    /** Kendi VPS'inizde / bilgisayarınızda çalışan Hermes Agent. */
    HERMES,

    /** OpenAI uyumlu herhangi bir API (xAI Grok, OpenRouter, Groq, vLLM, LM Studio...). */
    OPENAI,

    /** Ollama sunucusu (yerel modeller). */
    OLLAMA,
    ;

    val label: String
        get() = when (this) {
            HERMES -> "Hermes Agent"
            OPENAI -> "OpenAI uyumlu"
            OLLAMA -> "Ollama"
        }
}

/** Bir sunucu/bağlantı profili. Birden çok VPS veya ev bilgisayarı tanımlanabilir. */
@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val kind: ServerKind = ServerKind.HERMES,
    val baseUrl: String,
    val token: String = "",
    val note: String = "",
    val createdAt: Long = 0L,
) {
    /** Sunucu tarafında bot kaydı/zamanlama desteği var mı? */
    val supportsRemoteBots: Boolean get() = kind == ServerKind.HERMES
}

enum class ScheduleMode {
    OFF,
    INTERVAL,
    DAILY,
    ;

    val label: String
        get() = when (this) {
            OFF -> "Kapalı"
            INTERVAL -> "Aralıklı"
            DAILY -> "Her gün"
        }
}

/** Botun kendi kendine çalışma ayarı. */
@Serializable
data class Schedule(
    val mode: ScheduleMode = ScheduleMode.OFF,
    @SerialName("every_minutes") val everyMinutes: Int = 60,
    @SerialName("at_hour") val atHour: Int = 9,
    @SerialName("at_minute") val atMinute: Int = 0,
    val prompt: String = "",
    val notify: Boolean = true,
    /** Telefonun saat dilimi farkı (dakika). Sunucu "her gün 08:30" derken bunu kullanır. */
    @SerialName("tz_offset_minutes") val tzOffsetMinutes: Int = 180,
) {
    val isActive: Boolean get() = mode != ScheduleMode.OFF && prompt.isNotBlank()
}

/** Bir botun tüm tanımı. */
@Serializable
data class BotSpec(
    val id: String,
    val name: String,
    val avatar: String = "🤖",
    @SerialName("server_id") val serverId: String = "",
    /** Hermes Agent'ta hangi model sağlayıcısının kullanılacağı: echo, xai, openai, ollama, anthropic... */
    val backend: String = "",
    val model: String = "",
    @SerialName("system_prompt") val systemPrompt: String = "",
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    @SerialName("memory_turns") val memoryTurns: Int = 12,
    val tools: List<String> = emptyList(),
    val schedule: Schedule = Schedule(),
    val enabled: Boolean = true,
    val color: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
)

@Serializable
enum class Role {
    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("tool")
    TOOL,
    ;

    /** API'lerde kullanılan küçük harfli ad. */
    val wire: String get() = name.lowercase()
}

/** Sohbet geçmişindeki tek bir mesaj. */
@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val ts: Long = 0L,
    @SerialName("tool_name") val toolName: String? = null,
    val streaming: Boolean = false,
    val error: Boolean = false,
    @SerialName("bot_id") val botId: String = "",
)

/** Bir çalıştırma (run) sırasında akan olaylar. */
sealed interface RunEvent {
    data class Started(val runId: String) : RunEvent

    /** Akan yanıt parçası. */
    data class Delta(val text: String) : RunEvent

    data class ToolCall(val name: String, val args: String, val callId: String = "") : RunEvent

    data class ToolResult(
        val name: String,
        val result: String,
        val ok: Boolean = true,
        val callId: String = "",
    ) : RunEvent

    /** Tamamlanmış asistan mesajı. */
    data class Message(val content: String) : RunEvent

    data class Usage(val promptTokens: Int, val completionTokens: Int) : RunEvent

    data class Finished(val status: String = "ok", val durationMs: Long = 0L) : RunEvent

    data class Failed(val message: String) : RunEvent

    data class Log(val line: String) : RunEvent
}

/** /v1/health yanıtı. */
@Serializable
data class AgentInfo(
    val ok: Boolean = true,
    val name: String = "",
    val version: String = "",
    @SerialName("uptime_s") val uptimeSeconds: Long = 0L,
    val host: String = "",
    val bots: Int = 0,
    val backends: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
)

@Serializable
data class RunSummary(
    @SerialName("run_id") val runId: String,
    @SerialName("bot_id") val botId: String = "",
    val status: String = "",
    @SerialName("started_at") val startedAt: Double = 0.0,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    val preview: String = "",
)

enum class BotStatus { IDLE, RUNNING, ERROR, DISABLED }

enum class ConnectionState { UNKNOWN, CHECKING, ONLINE, OFFLINE }

/** Çekirdek katmanın fırlattığı tek hata tipi. */
class HermesException(
    message: String,
    val statusCode: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause)
