package com.fatsar.hermes.core.store

import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HermesJson
import com.fatsar.hermes.core.net.HermesPrettyJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Uygulamanın kalıcı durumu (sunucular + botlar + tercihler). */
@Serializable
data class AppData(
    val version: Int = 1,
    val servers: List<ServerProfile> = emptyList(),
    val bots: List<BotSpec> = emptyList(),
    @SerialName("active_bot_id") val activeBotId: String = "",
    @SerialName("last_runs") val lastRuns: Map<String, Long> = emptyMap(),
    @SerialName("background_enabled") val backgroundEnabled: Boolean = true,
    @SerialName("onboarded") val onboarded: Boolean = false,
)

/**
 * Tüm kalıcı veriyi yöneten tek nokta. Bozuk JSON'da uygulama çökmez,
 * varsayılan boş duruma düşer.
 */
class Repository(private val storage: Storage) {

    companion object {
        const val STATE_FILE = "hermes_state.json"
        const val TRANSCRIPT_PREFIX = "chat_"
        const val MAX_MESSAGES_PER_BOT = 400
    }

    fun load(): AppData {
        val raw = storage.read(STATE_FILE) ?: return AppData()
        return runCatching { HermesJson.decodeFromString(AppData.serializer(), raw) }.getOrElse { AppData() }
    }

    fun save(data: AppData) {
        storage.write(STATE_FILE, HermesPrettyJson.encodeToString(AppData.serializer(), data))
    }

    private fun transcriptName(botId: String) = "$TRANSCRIPT_PREFIX$botId.json"

    fun transcript(botId: String): List<ChatMessage> {
        val raw = storage.read(transcriptName(botId)) ?: return emptyList()
        return runCatching {
            HermesJson.decodeFromString(ListSerializer(ChatMessage.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun saveTranscript(botId: String, messages: List<ChatMessage>) {
        val trimmed = if (messages.size > MAX_MESSAGES_PER_BOT) {
            messages.takeLast(MAX_MESSAGES_PER_BOT)
        } else {
            messages
        }
        storage.write(
            transcriptName(botId),
            HermesJson.encodeToString(ListSerializer(ChatMessage.serializer()), trimmed),
        )
    }

    fun appendMessage(botId: String, message: ChatMessage) {
        saveTranscript(botId, transcript(botId) + message)
    }

    fun clearTranscript(botId: String) {
        storage.delete(transcriptName(botId))
    }

    /** Bot silindiğinde sohbet geçmişi de gider. */
    fun deleteBotData(botId: String) = clearTranscript(botId)

    /** Yedekleme: tüm ayarlar tek JSON metni olarak. */
    fun exportJson(): String {
        val data = load()
        return HermesPrettyJson.encodeToString(AppData.serializer(), data)
    }

    /** Yedekten geri yükleme. Hatalı JSON'da false döner ve mevcut veri korunur. */
    fun importJson(raw: String): Boolean {
        val parsed = runCatching { HermesJson.decodeFromString(AppData.serializer(), raw) }.getOrNull() ?: return false
        save(parsed)
        return true
    }
}
