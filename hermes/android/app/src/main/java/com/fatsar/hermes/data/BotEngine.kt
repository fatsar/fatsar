package com.fatsar.hermes.data

import android.content.Context
import com.fatsar.hermes.core.backend.Backend
import com.fatsar.hermes.core.backend.Backends
import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.logic.Pairing
import com.fatsar.hermes.core.logic.Prompts
import com.fatsar.hermes.core.logic.Scheduler
import com.fatsar.hermes.core.logic.Urls
import com.fatsar.hermes.core.model.AgentInfo
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.BotStatus
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.ConnectionState
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.Role
import com.fatsar.hermes.core.model.RunSummary
import com.fatsar.hermes.core.model.ServerKind
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
 * Uygulamanın tüm çalışma zamanı durumu: sunucular, botlar, sohbetler, akışlar.
 * Application ömrü boyunca yaşar; ekranlar yalnızca akışları dinler.
 */
class BotEngine(
    private val context: Context,
    private val repository: Repository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

    private fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        repository.save(next)
    }

    fun notice(text: String) {
        scope.launch { _messages.emit(text) }
    }

    // -------------------------------------------------------------- sunucu

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
    }

    /** Agent'ın yazdırdığı "HERMES1:..." kodundan sunucu ekler. */
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

    fun checkConnection(serverId: String) {
        val profile = server(serverId) ?: return
        _connections.update { it + (serverId to ConnectionState.CHECKING) }
        scope.launch {
            val result = runCatching { backendOf(profile).health() }
            result.onSuccess { info ->
                _agents.update { it + (serverId to info) }
                _connections.update { it + (serverId to ConnectionState.ONLINE) }
            }.onFailure { error ->
                _connections.update { it + (serverId to ConnectionState.OFFLINE) }
                notice(HttpTransport.friendlyError(error))
            }
        }
    }

    fun refreshConnections() {
        _data.value.servers.forEach { checkConnection(it.id) }
    }

    fun loadModels(serverId: String) {
        val profile = server(serverId) ?: return
        scope.launch {
            runCatching { backendOf(profile).models() }
                .onSuccess { list -> _models.update { it + (serverId to list) } }
                .onFailure { notice("Model listesi alınamadı: " + HttpTransport.friendlyError(it)) }
        }
    }

    private fun backendOf(profile: ServerProfile): Backend = Backends.create(profile, transport)

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
        // Hermes Agent'ta tanımlı botlar sunucuda da güncellensin (zamanlama orada çalışır).
        val profile = serverFor(clean)
        if (profile != null && profile.kind == ServerKind.HERMES) {
            scope.launch {
                runCatching { backendOf(profile).pushBot(clean) }
                    .onFailure { notice("Bot sunucuya yazılamadı: " + HttpTransport.friendlyError(it)) }
            }
        }
    }

    fun deleteBot(botId: String) {
        val bot = bot(botId) ?: return
        val profile = serverFor(bot)
        jobs.remove(botId)?.cancel()
        update { data ->
            data.copy(
                bots = data.bots.filterNot { it.id == botId },
                lastRuns = data.lastRuns - botId,
                activeBotId = if (data.activeBotId == botId) "" else data.activeBotId,
            )
        }
        repository.deleteBotData(botId)
        _chats.update { it - botId }
        _status.update { it - botId }
        if (profile != null && profile.kind == ServerKind.HERMES) {
            scope.launch { runCatching { backendOf(profile).deleteBot(botId) } }
        }
    }

    fun duplicateBot(botId: String) {
        val bot = bot(botId) ?: return
        val now = System.currentTimeMillis()
        upsertBot(bot.copy(id = Ids.newId("bot", now), name = bot.name + " (kopya)", createdAt = now))
    }

    fun setActiveBot(botId: String) = update { it.copy(activeBotId = botId) }

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
        val profile = serverFor(bot) ?: return
        if (profile.kind == ServerKind.HERMES) {
            scope.launch {
                runCatching {
                    transport.request(
                        Urls.hermesUrl(profile.baseUrl, "/v1/bots/$botId/messages"),
                        "DELETE",
                        mapOf("Authorization" to "Bearer ${profile.token}"),
                    )
                }
            }
        }
    }

    fun isRunning(botId: String): Boolean = jobs[botId]?.isActive == true

    fun stop(botId: String) {
        jobs.remove(botId)?.cancel()
        updateChat(botId) { list ->
            list.map { if (it.streaming) it.copy(streaming = false, content = it.content.ifBlank { "(durduruldu)" }) else it }
        }
        persist(botId)
        setStatus(botId, BotStatus.IDLE)
    }

    private fun setStatus(botId: String, value: BotStatus) {
        _status.update { it + (botId to value) }
    }

    /** Kullanıcı mesajını gönderir ve yanıtı akıtır. */
    fun send(botId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val bot = bot(botId) ?: return
        val profile = serverFor(bot)
        if (profile == null) {
            notice("Önce Sunucular ekranından bir bağlantı ekleyin.")
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

        val backend = backendOf(profile)
        val builder = StringBuilder()
        jobs[botId] = scope.launch {
            try {
                backend.run(bot, history, trimmed).collect { event ->
                    handleEvent(botId, placeholderId, builder, event)
                }
            } catch (e: Exception) {
                appendError(botId, placeholderId, HttpTransport.friendlyError(e))
            } finally {
                updateChat(botId) { list ->
                    list.map { if (it.id == placeholderId) it.copy(streaming = false) else it }
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
                val label = "${event.name}(${Prompts.preview(event.args, 120)})"
                insertBeforeAssistant(
                    botId,
                    messageId,
                    ChatMessage(
                        id = toolId,
                        role = Role.TOOL,
                        content = label,
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

            is RunEvent.Usage -> {
                _usage.update {
                    it + (botId to "${event.promptTokens}+${event.completionTokens} token")
                }
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

    // ---------------------------------------------------------- zamanlama

    /** Yalnızca telefonda çalışması gereken botlar (Hermes Agent kendi zamanlamasını yürütür). */
    fun locallyScheduledBots(): List<BotSpec> = _data.value.bots.filter {
        it.enabled && it.schedule.isActive && serverFor(it)?.kind != ServerKind.HERMES
    }

    fun nextLocalTick(): Long = Scheduler.tickInterval(locallyScheduledBots())

    /** Arka plan servisi tarafından çağrılır. Zamanı gelen yerel botları çalıştırır. */
    suspend fun runDueSchedules() {
        val now = System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(now)
        val due = Scheduler.dueBots(locallyScheduledBots(), _data.value.lastRuns, now, offset)
        for (bot in due) {
            markLastRun(bot.id, now)
            runScheduled(bot)
        }
    }

    private fun markLastRun(botId: String, at: Long) {
        update { it.copy(lastRuns = it.lastRuns + (botId to at)) }
    }

    private suspend fun runScheduled(bot: BotSpec) {
        val profile = serverFor(bot) ?: return
        val prompt = bot.schedule.prompt
        ensureChatLoaded(bot.id)
        setStatus(bot.id, BotStatus.RUNNING)
        val builder = StringBuilder()
        val now = System.currentTimeMillis()
        val history = messagesOf(bot.id)
        runCatching {
            backendOf(profile).run(bot, history, prompt).collect { event ->
                when (event) {
                    is RunEvent.Delta -> builder.append(event.text)
                    is RunEvent.Message -> if (builder.isEmpty()) builder.append(event.content)
                    is RunEvent.Failed -> builder.append("\n⚠️ ").append(event.message)
                    else -> Unit
                }
            }
        }.onFailure { builder.append("\n⚠️ ").append(HttpTransport.friendlyError(it)) }

        val answer = builder.toString().trim()
        updateChat(bot.id) { list ->
            list +
                ChatMessage(Ids.newId("m", now), Role.USER, "⏰ $prompt", now, botId = bot.id) +
                ChatMessage(Ids.newId("m", now + 1), Role.ASSISTANT, answer, now + 1, botId = bot.id)
        }
        persist(bot.id)
        setStatus(bot.id, BotStatus.IDLE)
        if (bot.schedule.notify && answer.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                Notifications.postResult(context, bot.id, "${bot.avatar} ${bot.name}", answer)
            }
        }
    }

    /** Hermes Agent üzerindeki çalışma geçmişi. */
    suspend fun runHistory(botId: String, limit: Int = 20): List<RunSummary> {
        val bot = bot(botId) ?: return emptyList()
        val profile = serverFor(bot) ?: return emptyList()
        if (profile.kind != ServerKind.HERMES) return emptyList()
        return runCatching { backendOf(profile).runHistory(botId, limit) }.getOrElse { emptyList() }
    }

    /** Agent'ta kayıtlı botları telefona indirir. */
    fun importBotsFromAgent(serverId: String) {
        val profile = server(serverId) ?: return
        if (profile.kind != ServerKind.HERMES) return
        scope.launch {
            runCatching { backendOf(profile).listBots() }
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
