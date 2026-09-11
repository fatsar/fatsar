package com.fatsar.hermes.core.backend

import com.fatsar.hermes.core.model.AgentInfo
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.RunSummary
import com.fatsar.hermes.core.model.ServerKind
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HttpTransport
import kotlinx.coroutines.flow.Flow

/**
 * Bir bota "koşturma" yeteneği sağlayan uç nokta soyutlaması.
 * Uygulama katmanı yalnızca bu arayüzü tanır.
 */
interface Backend {
    val profile: ServerProfile

    /** Bağlantı testi / sunucu bilgisi. */
    suspend fun health(): AgentInfo

    /** Kullanılabilir model adları. */
    suspend fun models(): List<String>

    /** Botu çalıştırır ve olayları akıtır. Akış asla istisna fırlatmaz; hata [RunEvent.Failed] olarak gelir. */
    fun run(bot: BotSpec, history: List<ChatMessage>, input: String): Flow<RunEvent>

    /** Sunucu tarafında bot kaydı tutan uçlar için (Hermes Agent). */
    suspend fun listBots(): List<BotSpec> = emptyList()

    suspend fun pushBot(bot: BotSpec) = Unit

    suspend fun deleteBot(botId: String) = Unit

    suspend fun runHistory(botId: String, limit: Int = 20): List<RunSummary> = emptyList()

    suspend fun cancel(runId: String) = Unit
}

object Backends {
    fun create(profile: ServerProfile, transport: HttpTransport): Backend = when (profile.kind) {
        ServerKind.HERMES -> HermesBackend(profile, transport)
        ServerKind.OPENAI -> OpenAiBackend(profile, transport)
        ServerKind.OLLAMA -> OllamaBackend(profile, transport)
    }
}
