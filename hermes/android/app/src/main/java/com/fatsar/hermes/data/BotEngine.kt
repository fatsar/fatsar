package com.fatsar.hermes.data

import android.content.Context
import com.fatsar.hermes.core.backend.HermesClient
import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.logic.Pairing
import com.fatsar.hermes.core.logic.Prompts
import com.fatsar.hermes.core.logic.Urls
import com.fatsar.hermes.core.model.AgentInfo
import com.fatsar.hermes.core.model.BackendInfo
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.BotStatus
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.ConnectionState
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.Role
import com.fatsar.hermes.core.model.RunSummary
import com.fatsar.hermes.core.model.ScheduleMode
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HttpTransport
import com.fatsar.hermes.core.store.AppData
import com.fatsar.hermes.core.store.Repository
import com.fatsar.hermes.service.Notifications
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Uygulamanın tüm çalışma zamanı durumu.
 *
 * Uygulama yalnızca kendi sunucunuzdaki **Hermes Agent**'a bağlanır: model
 * sağlayıcıları, anahtarlar ve zamanlama agent'ta yaşar. Telefon bu yüzden
 * yalnızca agent'ın hazır bildirdiği sağlayıcılar arasından seçim yapar.
 */
class BotEngine(
    private val context: Context,
    private val repository: Repository,
) {

    // Ağ çağrıları, akış toplama ve disk yazma ana iş parçacığında yapılmaz.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport = HttpTransport()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _data = MutableStateFlow(repository.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _chats = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val chats: StateFlow<Map<String, List<ChatMessage>>> = _chats.asStateFlow()

    private val _status = MutableStateFlow<Map<String, BotStatus>>(emptyMap())
    val status: StateFlow<Map<String, BotStatus>> = _status.asStateFlow()

    private val _connections = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connections: StateFlow<Map<String, ConnectionState>> = _connections.asStateFlow()

    private val _agents = MutableStateFlow<Map<String, AgentInfo>>(emptyMap())
    val agents: StateFlow<Map<String, AgentInfo>> = _agents.asStateFlow()

    /** sunucu kimliği -> agent'ta tanımlı LLM sağlayıcıları */
    private val _backends = MutableStateFlow<Map<String, List<BackendInfo>>>(emptyMap())
    val backends: StateFlow<Map<String, List<BackendInfo>>> = _backends.asStateFlow()

    /** "sunucuKimliği|sağlayıcı" -> model adları */
    private val _models = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val models: StateFlow<Map<String, List<String>>> = _models.asStateFlow()

    private val _usage = MutableStateFlow<Map<String, String>>(emptyMap())
    val usage: StateFlow<Map<String, String>> = _usage.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Ekranlarda kısa bilgi (snackbar) olarak gösterilecek mesajlar. */
    val notices: SharedFlow<String> = _messages.asSharedFlow()

    // ----------------------------------------------------------------- veri

    fun servers(): List<ServerProfile> = _data.value.servers

    fun bots(): List<BotSpec> = _data.value.bots

    fun bot(id: String?): BotSpec? = _data.value.bots.firstOrNull { it.id == id }

    fun server(id: String?): ServerProfile? = _data.value.servers.firstOrNull { it.id == id }

    fun serverFor(bot: BotSpec): ServerProfile? = server(bot.serverId) ?: _data.value.servers.firstOrNull()

    private fun clientFor(profile: ServerProfile) = HermesClient(profile, transport)

    private fun clientFor(bot: BotSpec): HermesClient? = serverFor(bot)?.let { clientFor(it) }

    private fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        repository.save(next)
    }

    fun notice(text: String) {
        scope.launch { _messages.emit(text) }
    }

    // ----------------------------------------------------------- sunucular

    fun upsertServer(profile: ServerProfile) {
        val normalized = profile.copy(baseUrl = Urls.normalizeBase(profile.baseUrl))
        update { data ->
            val exists = data.servers.any { it.id == normalized.id }
            data.copy(
                servers = if (exists) {
                    data.servers.map { if (it.id == normalized.id) normalized else it }
                } else {
                    data.servers + normalized
                },
            )
        }
        checkConnection(normalized.id)
    }

    fun deleteServer(serverId: String) {
        update { data ->
            val remaining = data.servers.filterNot { it.id == serverId }
            val fallback = remaining.firstOrNull()?.id.orEmpty()
            data.copy(
                servers = remaining,
                bots = data.bots.map { if (it.serverId == serverId) it.copy(serverId = fallback) else it },
            )
        }
        _connections.update { it - serverId }
        _agents.update { it - serverId }
        _backends.update { it - serverId }
    }

    /** Agent'ın yazdırdığı "HERMES1:..." kodundan ya da hermes:// bağlantısından sunucu ekler. */
    fun addFromPairing(code: String): Boolean {
        val payload = Pairing.decode(code) ?: return false
        val now = System.currentTimeMillis()
        val existing = _data.value.servers.firstOrNull {
            Urls.normalizeBase(it.baseUrl) == Urls.normalizeBase(payload.url)
        }
        val profile = Pairing.toProfile(payload, existing?.id ?: Ids.newId("srv", now), now)
        upsertServer(profile)
        update { it.copy(onboarded = true) }
        return true
    }

    /** Bağlantıyı sınar; başarılıysa agent bilgisi ve sağlayıcı listesi de tazelenir. */
    fun checkConnection(serverId: String) {
        val profile = server(serverId) ?: return
        _connections.update { it + (serverId to ConnectionState.CHECKING) }
        scope.launch {
            runCatching { clientFor(profile).health() }
                .onSuccess { info ->
                    _agents.update { it + (serverId to info) }
                    _connections.update { it + (serverId to ConnectionState.ONLINE) }
                    loadBackends(serverId)
                }
                .onFailure { error ->
                    _connections.update { it + (serverId to ConnectionState.OFFLINE) }
                    notice(HttpTransport.friendlyError(error))
                }
        }
    }

    fun refreshConnections() {
        _data.value.servers.forEach { checkConnection(it.id) }
    }

    /** Agent'ta tanımlı LLM sağlayıcılarını getirir (anahtarlar telefona inmez). */
    fun loadBackends(serverId: String) {
        val profile = server(serverId) ?: return
        scope.launch {
            runCatching { clientFor(profile).backends() }
                .onSuccess { list -> _backends.update { it + (serverId to list) } }
                .onFailure { notice("Sağlayıcı listesi alınamadı: " + HttpTransport.friendlyError(it)) }
        }
    }

    fun modelsKey(serverId: String, backend: String) = "$serverId|$backend"

    /** Seçilen sağlayıcının agent üzerinden görünen modellerini getirir. */
    fun loadModels(serverId: String, backend: String) {
        val profile = server(serverId) ?: return
        val key = modelsKey(serverId, backend)
        scope.launch {
            runCatching { clientFor(profile).models(backend) }
                .onSuccess { list -> _models.update { it + (key to list) } }
                .onFailure {
                    _models.update { it + (key to emptyList()) }
                    notice("Model listesi alınamadı: " + HttpTransport.friendlyError(it))
                }
        }
    }

    /** Bir sunucuda kullanıma hazır sağlayıcılar. */
    fun readyBackends(serverId: String?): List<BackendInfo> =
        _backends.value[serverId].orEmpty().filter { it.ready }

    // ----------------------------------------------------------------- bot

    fun upsertBot(bot: BotSpec) {
        val now = System.currentTimeMillis()
        val clean = bot.copy(name = bot.name.trim().ifBlank { "Adsız bot" }, updatedAt = now)
        update { data ->
            val exists = data.bots.any { it.id == clean.id }
            data.copy(
                bots = if (exists) data.bots.map { if (it.id == clean.id) clean else it } else data.bots + clean,
            )
        }
        // Bot tanımı agent'a yazılır: zamanlama orada çalışır, telefon kapalıyken de.
        clientFor(clean)?.let { client ->
            scope.launch {
                runCatching { client.pushBot(clean) }
                    .onFailure { notice("Bot sunucuya yazılamadı: " + HttpTransport.friendlyError(it)) }
            }
        }
    }

    fun deleteBot(botId: String) {
        val bot = bot(botId) ?: return
        val client = clientFor(bot)
        jobs.remove(botId)?.cancel()
        update { data ->
            data.copy(
                bots = data.bots.filterNot { it.id == botId },
                seenRuns = data.seenRuns - botId,
                activeBotId = if (data.activeBotId == botId) "" else data.activeBotId,
            )
        }
        repository.deleteBotData(botId)
        _chats.update { it - botId }
        _status.update { it - botId }
        client?.let { scope.launch { runCatching { it.deleteBot(botId) } } }
    }

    fun duplicateBot(botId: String) {
        val bot = bot(botId) ?: return
        val now = System.currentTimeMillis()
        upsertBot(bot.copy(id = Ids.newId("bot", now), name = bot.name + " (kopya)", createdAt = now))
    }

    fun setActiveBot(botId: String) = update { it.copy(activeBotId = botId) }

    /** Zamanlanmış botlar (hepsi agent'ta çalışır). */
    fun scheduledBots(): List<BotSpec> = _data.value.bots.filter { it.enabled && it.schedule.isActive }

    // -------------------------------------------------------------- sohbet

    fun ensureChatLoaded(botId: String) {
        if (_chats.value.containsKey(botId)) return
        _chats.update { it + (botId to repository.transcript(botId)) }
    }

    fun messagesOf(botId: String): List<ChatMessage> = _chats.value[botId].orEmpty()

    private fun updateChat(botId: String, transform: (List<ChatMessage>) -> List<ChatMessage>) {
        _chats.update { all -> all + (botId to transform(all[botId].orEmpty())) }
    }

    private fun persist(botId: String) {
        repository.saveTranscript(botId, messagesOf(botId))
    }

    fun clearChat(botId: String) {
        _chats.update { it + (botId to emptyList()) }
        repository.clearTranscript(botId)
        val bot = bot(botId) ?: return
        clientFor(bot)?.let { client -> scope.launch { client.clearMessages(botId) } }
    }

    fun isRunning(botId: String): Boolean = jobs[botId]?.isActive == true

    fun stop(botId: String) {
        jobs.remove(botId)?.cancel()
        updateChat(botId) { list ->
            list.map {
                if (it.streaming) {
                    it.copy(streaming = false, content = it.content.ifBlank { "(durduruldu)" })
                } else {
                    it
                }
            }
        }
        persist(botId)
        setStatus(botId, BotStatus.IDLE)
    }

    private fun setStatus(botId: String, value: BotStatus) {
        _status.update { it + (botId to value) }
    }

    /** Kullanıcı mesajını agent'a gönderir ve yanıtı akıtır. */
    fun send(botId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val bot = bot(botId) ?: return
        val client = clientFor(bot)
        if (client == null) {
            notice("Önce Sunucular ekranından Hermes Agent bağlantınızı ekleyin.")
            return
        }
        if (isRunning(botId)) {
            notice("Bot hâlâ çalışıyor. Önce durdurun.")
            return
        }

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(Ids.newId("m", now), Role.USER, trimmed, now, botId = botId)
        val placeholderId = Ids.newId("m", now + 1)
        val placeholder = ChatMessage(placeholderId, Role.ASSISTANT, "", now + 1, streaming = true, botId = botId)
        val history = messagesOf(botId)
        updateChat(botId) { it + userMessage + placeholder }
        persist(botId)
        setStatus(botId, BotStatus.RUNNING)

        val builder = StringBuilder()
        jobs[botId] = scope.launch {
            try {
                client.run(bot, history, trimmed).collect { event ->
                    handleEvent(botId, placeholderId, builder, event)
                }
            } catch (e: Exception) {
                appendError(botId, placeholderId, HttpTransport.friendlyError(e))
            } finally {
                updateChat(botId) { list ->
                    list.map { if (it.streaming) it.copy(streaming = false) else it }
                }
                persist(botId)
                setStatus(botId, if (statusIsError(botId, placeholderId)) BotStatus.ERROR else BotStatus.IDLE)
                jobs.remove(botId)
            }
        }
    }

    private fun statusIsError(botId: String, messageId: String): Boolean =
        messagesOf(botId).firstOrNull { it.id == messageId }?.error == true

    private fun handleEvent(botId: String, messageId: String, builder: StringBuilder, event: RunEvent) {
        when (event) {
            is RunEvent.Started -> Unit

            is RunEvent.Delta -> {
                builder.append(event.text)
                val snapshot = builder.toString()
                updateChat(botId) { list ->
                    list.map { if (it.id == messageId) it.copy(content = snapshot) else it }
                }
            }

            is RunEvent.Message -> {
                if (builder.isEmpty()) {
                    builder.append(event.content)
                    val snapshot = builder.toString()
                    updateChat(botId) { list ->
                        list.map { if (it.id == messageId) it.copy(content = snapshot) else it }
                    }
                }
            }

            is RunEvent.ToolCall -> {
                val toolId = "tool_" + event.callId.ifBlank { Ids.newId("c", System.currentTimeMillis()) }
                insertBeforeAssistant(
                    botId,
                    messageId,
                    ChatMessage(
                        id = toolId,
                        role = Role.TOOL,
                        content = "${event.name}(${Prompts.preview(event.args, 120)})",
                        ts = System.currentTimeMillis(),
                        toolName = event.name,
                        streaming = true,
                        botId = botId,
                    ),
                )
            }

            is RunEvent.ToolResult -> {
                val toolId = "tool_" + event.callId
                updateChat(botId) { list ->
                    list.map {
                        if (it.id == toolId) {
                            it.copy(
                                content = it.content + "\n→ " + Prompts.preview(event.result, 400),
                                streaming = false,
                                error = !event.ok,
                            )
                        } else {
                            it
                        }
                    }
                }
                persist(botId)
            }

            is RunEvent.Usage -> _usage.update {
                it + (botId to "${event.promptTokens}+${event.completionTokens} token")
            }

            is RunEvent.Failed -> appendError(botId, messageId, event.message)

            is RunEvent.Finished -> Unit

            is RunEvent.Log -> Unit
        }
    }

    private fun insertBeforeAssistant(botId: String, assistantId: String, message: ChatMessage) {
        updateChat(botId) { list ->
            val index = list.indexOfFirst { it.id == assistantId }
            if (index < 0) list + message else list.toMutableList().apply { add(index, message) }
        }
    }

    private fun appendError(botId: String, messageId: String, text: String) {
        updateChat(botId) { list ->
            list.map {
                if (it.id == messageId) {
                    it.copy(
                        content = if (it.content.isBlank()) text else it.content + "\n\n⚠️ " + text,
                        error = true,
                        streaming = false,
                    )
                } else {
                    it
                }
            }
        }
        setStatus(botId, BotStatus.ERROR)
    }

    // -------------------------------------------- agent'taki zamanlı işler

    /** Agent üzerindeki çalışma geçmişi (ekranda göstermek için). */
    suspend fun runHistory(botId: String, limit: Int = 20): List<RunSummary> {
        val bot = bot(botId) ?: return emptyList()
        val client = clientFor(bot) ?: return emptyList()
        return runCatching { client.runHistory(botId, limit) }.getOrElse { emptyList() }
    }

    /**
     * Agent'ta çalışmış zamanlanmış görevlerin yeni sonuçlarını telefona getirir:
     * sohbete ekler ve (bot bildirim istiyorsa) bildirim gösterir.
     * Arka plan servisi tarafından çağrılır.
     */
    suspend fun pollScheduledResults() {
        for (bot in scheduledBots()) {
            val client = clientFor(bot) ?: continue
            val runs = runCatching { client.runHistory(bot.id, 5) }.getOrNull() ?: continue
            val newest = runs.firstOrNull { it.isScheduled && it.status == "ok" } ?: continue
            val seen = _data.value.seenRuns[bot.id]
            if (seen == newest.runId) continue

            update { it.copy(seenRuns = it.seenRuns + (bot.id to newest.runId)) }
            // İlk eşitlemede geçmişi bildirimle doldurma; yalnızca kaydı işaretle.
            if (seen.isNullOrEmpty()) continue

            val detail = runCatching { client.runDetail(newest.runId) }.getOrNull() ?: continue
            val output = detail.output.ifBlank { detail.error }
            if (output.isBlank()) continue

            ensureChatLoaded(bot.id)
            val now = System.currentTimeMillis()
            updateChat(bot.id) { list ->
                list +
                    ChatMessage(Ids.newId("m", now), Role.USER, "⏰ ${detail.input}", now, botId = bot.id) +
                    ChatMessage(Ids.newId("m", now + 1), Role.ASSISTANT, output, now + 1, botId = bot.id)
            }
            persist(bot.id)

            if (bot.schedule.notify) {
                withContext(Dispatchers.Main) {
                    Notifications.postResult(context, bot.id, "${bot.avatar} ${bot.name}", output)
                }
            }
        }
    }

    /** Sonuçların ne sıklıkta getirileceği: en sık zamanlanan bota göre, 5–30 dk arası. */
    fun pollIntervalMillis(): Long {
        val fastest = scheduledBots()
            .filter { it.schedule.mode == ScheduleMode.INTERVAL }
            .minOfOrNull { it.schedule.everyMinutes.coerceAtLeast(1) * 60_000L }
            ?: 15 * 60_000L
        return fastest.coerceIn(5 * 60_000L, 30 * 60_000L)
    }

    /** Agent'ta kayıtlı botları telefona indirir. */
    fun importBotsFromAgent(serverId: String) {
        val profile = server(serverId) ?: return
        scope.launch {
            runCatching { clientFor(profile).listBots() }
                .onSuccess { remote ->
                    if (remote.isEmpty()) {
                        notice("Sunucuda kayıtlı bot yok.")
                        return@onSuccess
                    }
                    var added = 0
                    remote.forEach { bot ->
                        if (_data.value.bots.none { it.id == bot.id }) {
                            added++
                            update { data -> data.copy(bots = data.bots + bot.copy(serverId = serverId)) }
                        }
                    }
                    notice(if (added > 0) "$added bot içe aktarıldı." else "Tüm botlar zaten mevcut.")
                }
                .onFailure { notice("Botlar alınamadı: " + HttpTransport.friendlyError(it)) }
        }
    }

    /** Telefonun saat dilimi (agent "her gün 08:30"u buna göre hesaplar). */
    fun timeZoneOffsetMinutes(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000

    // -------------------------------------------------------------- ayarlar

    fun setBackgroundEnabled(enabled: Boolean) = update { it.copy(backgroundEnabled = enabled) }

    fun markOnboarded() = update { it.copy(onboarded = true) }

    fun exportSettings(): String = repository.exportJson()

    fun importSettings(json: String): Boolean {
        val ok = repository.importJson(json)
        if (ok) {
            _data.value = repository.load()
            _chats.value = emptyMap()
            refreshConnections()
        }
        return ok
    }
}
