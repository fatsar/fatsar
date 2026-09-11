package com.fatsar.hermes.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.logic.Templates
import com.fatsar.hermes.core.logic.Tools
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.Schedule
import com.fatsar.hermes.core.model.ScheduleMode
import com.fatsar.hermes.core.model.ServerKind
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.*
import java.util.TimeZone

private val AVATARS = listOf(
    "🤖", "🧠", "🛡️", "📰", "💻", "🌍", "✍️", "📊", "🔎", "🚀", "🧰", "🎯", "🐧", "🦾", "📮", "🧪",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotEditScreen(
    engine: BotEngine,
    botId: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    onAddServer: () -> Unit,
) {
    val data by engine.data.collectAsState()
    val agents by engine.agents.collectAsState()
    val modelsByServer by engine.models.collectAsState()
    val existing = remember(botId, data.bots) { data.bots.firstOrNull { it.id == botId } }
    val isNew = existing == null

    var templateChosen by remember { mutableStateOf(!isNew) }
    var draft by remember {
        mutableStateOf(
            existing ?: BotSpec(
                id = Ids.newId("bot", System.currentTimeMillis()),
                name = "",
                serverId = data.servers.firstOrNull()?.id.orEmpty(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val server = remember(draft.serverId, data.servers) {
        data.servers.firstOrNull { it.id == draft.serverId } ?: data.servers.firstOrNull()
    }
    val isHermes = server?.kind == ServerKind.HERMES
    val models = modelsByServer[server?.id].orEmpty()

    LaunchedEffect(server?.id) {
        server?.id?.let { engine.loadModels(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") }
                },
                title = { Text(if (isNew) "Yeni bot" else "Botu düzenle", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(
                        onClick = {
                            val withTz = draft.copy(
                                schedule = draft.schedule.copy(
                                    tzOffsetMinutes = TimeZone.getDefault()
                                        .getOffset(System.currentTimeMillis()) / 60000,
                                ),
                                serverId = server?.id.orEmpty(),
                            )
                            engine.upsertBot(withTz)
                            onSaved(withTz.id)
                        },
                        enabled = draft.name.isNotBlank() || !templateChosen,
                    ) { Text("Kaydet") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!templateChosen) {
            TemplatePicker(
                modifier = Modifier.padding(padding),
                onPick = { template ->
                    draft = Templates.toBot(
                        template = template,
                        id = draft.id,
                        serverId = draft.serverId,
                        model = draft.model,
                        now = System.currentTimeMillis(),
                    )
                    templateChosen = true
                },
            )
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            SectionTitle("Kimlik")
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotAvatar(draft.avatar, size = 52)
                Spacer(Modifier.width(12.dp))
                HermesField(
                    label = "Bot adı",
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = "ör. Sunucu Bekçisi",
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AVATARS.forEach { emoji ->
                    Surface(
                        onClick = { draft = draft.copy(avatar = emoji) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (draft.avatar == emoji) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Text(emoji, Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }

            SectionTitle("Bağlantı")
            if (data.servers.isEmpty()) {
                InfoBanner("Henüz sunucu yok. Botun çalışması için bir Hermes Agent ya da API bağlantısı ekleyin.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddServer, shape = RoundedCornerShape(14.dp)) {
                    Text("Sunucu ekle")
                }
            } else {
                HermesDropdown(
                    label = "Sunucu",
                    value = server?.let { "${it.name} (${it.kind.label})" }.orEmpty(),
                    options = data.servers.map { "${it.name} (${it.kind.label})" },
                    onSelect = { label ->
                        val picked = data.servers.firstOrNull { "${it.name} (${it.kind.label})" == label }
                        if (picked != null) draft = draft.copy(serverId = picked.id)
                    },
                )
                if (isHermes) {
                    Spacer(Modifier.height(10.dp))
                    val backends = agents[server?.id]?.backends.orEmpty()
                    HermesDropdown(
                        label = "Model sağlayıcı (agent üzerinde)",
                        value = draft.backend.ifBlank { "(agent varsayılanı)" },
                        options = listOf("(agent varsayılanı)") + backends,
                        onSelect = { draft = draft.copy(backend = if (it.startsWith("(")) "" else it) },
                        emptyHint = "Agent'a bağlanınca dolar",
                    )
                }
                Spacer(Modifier.height(10.dp))
                HermesDropdown(
                    label = "Model",
                    value = draft.model,
                    options = models,
                    onSelect = { draft = draft.copy(model = it) },
                    emptyHint = "Model listesi alınamadı — aşağıya elle yazın",
                )
                Spacer(Modifier.height(8.dp))
                HermesField(
                    label = "Model adı (elle)",
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    placeholder = "grok-3 / gpt-4o-mini / llama3.1:8b",
                )
            }

            SectionTitle("Görev tanımı")
            HermesField(
                label = "Sistem istemi",
                value = draft.systemPrompt,
                onValueChange = { draft = draft.copy(systemPrompt = it) },
                placeholder = "Bot nasıl davransın? Ne yapsın, neyi yapmasın?",
                singleLine = false,
                minLines = 5,
            )

            SectionTitle("Davranış")
            Text(
                "Yaratıcılık (temperature): ${"%.1f".format(draft.temperature)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = draft.temperature.toFloat(),
                onValueChange = { draft = draft.copy(temperature = (it * 10).toInt() / 10.0) },
                valueRange = 0f..2f,
                steps = 19,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HermesField(
                    label = "En fazla yanıt (token)",
                    value = draft.maxTokens.toString(),
                    onValueChange = { draft = draft.copy(maxTokens = it.filter { c -> c.isDigit() }.take(6).toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f),
                    keyboardNumeric = true,
                )
                HermesField(
                    label = "Hatırlanan tur",
                    value = draft.memoryTurns.toString(),
                    onValueChange = { draft = draft.copy(memoryTurns = it.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f),
                    keyboardNumeric = true,
                    supporting = "0 = hafızasız",
                )
            }

            SectionTitle("Araçlar")
            if (!isHermes) {
                InfoBanner(
                    "Araçlar yalnızca Hermes Agent bağlantısında çalışır. " +
                        "Doğrudan API bağlantısında bot sadece sohbet eder.",
                )
            } else {
                Text(
                    "Botun sunucuda kullanabileceği yetenekler.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val available = agents[server?.id]?.tools.orEmpty()
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Tools.all.forEach { tool ->
                        val enabledOnServer = available.isEmpty() || available.contains(tool.id)
                        ToggleChip(
                            label = tool.label + if (!enabledOnServer) " (agent'ta kapalı)" else "",
                            selected = draft.tools.contains(tool.id),
                            risky = tool.risky,
                            onToggle = {
                                draft = draft.copy(
                                    tools = if (draft.tools.contains(tool.id)) {
                                        draft.tools - tool.id
                                    } else {
                                        draft.tools + tool.id
                                    },
                                )
                            },
                        )
                    }
                }
                if (draft.tools.contains(Tools.SHELL)) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        "Kabuk aracı sunucuda komut çalıştırır. Agent'ın --allow-shell ile " +
                            "başlatılması ve komut izin listesi tanımlanması önerilir.",
                        tone = BannerTone.WARNING,
                    )
                }
            }

            SectionTitle("Zamanlama")
            ChipRow(
                options = listOf(
                    ScheduleMode.OFF.name to "Kapalı",
                    ScheduleMode.INTERVAL.name to "Aralıklı",
                    ScheduleMode.DAILY.name to "Her gün",
                ),
                selected = draft.schedule.mode.name,
                onSelect = { draft = draft.copy(schedule = draft.schedule.copy(mode = ScheduleMode.valueOf(it))) },
            )
            if (draft.schedule.mode != ScheduleMode.OFF) {
                Spacer(Modifier.height(10.dp))
                if (draft.schedule.mode == ScheduleMode.INTERVAL) {
                    HermesField(
                        label = "Kaç dakikada bir",
                        value = draft.schedule.everyMinutes.toString(),
                        onValueChange = {
                            val minutes = it.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0
                            draft = draft.copy(schedule = draft.schedule.copy(everyMinutes = minutes))
                        },
                        keyboardNumeric = true,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HermesField(
                            label = "Saat",
                            value = draft.schedule.atHour.toString(),
                            onValueChange = {
                                val h = it.filter { c -> c.isDigit() }.take(2).toIntOrNull() ?: 0
                                draft = draft.copy(schedule = draft.schedule.copy(atHour = h.coerceIn(0, 23)))
                            },
                            modifier = Modifier.weight(1f),
                            keyboardNumeric = true,
                        )
                        HermesField(
                            label = "Dakika",
                            value = draft.schedule.atMinute.toString(),
                            onValueChange = {
                                val m = it.filter { c -> c.isDigit() }.take(2).toIntOrNull() ?: 0
                                draft = draft.copy(schedule = draft.schedule.copy(atMinute = m.coerceIn(0, 59)))
                            },
                            modifier = Modifier.weight(1f),
                            keyboardNumeric = true,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HermesField(
                    label = "Çalıştırılacak istem",
                    value = draft.schedule.prompt,
                    onValueChange = { draft = draft.copy(schedule = draft.schedule.copy(prompt = it)) },
                    placeholder = "ör. Disk ve bellek durumunu özetle",
                    singleLine = false,
                    minLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Switch(
                        checked = draft.schedule.notify,
                        onCheckedChange = { draft = draft.copy(schedule = draft.schedule.copy(notify = it)) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Sonuç bildirimi göster", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                InfoBanner(
                    if (isHermes) {
                        "Bu bot sunucuda zamanlanır: telefon kapalı olsa bile çalışır."
                    } else {
                        "Doğrudan API bağlantısında zamanlama telefonda çalışır; " +
                            "uygulama arka planda kapatılırsa gecikebilir."
                    },
                )
            }

            SectionTitle("Durum")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) })
                Spacer(Modifier.width(10.dp))
                Text(if (draft.enabled) "Bot etkin" else "Bot duraklatıldı", style = MaterialTheme.typography.bodyMedium)
            }

            if (!isNew) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Botu sil") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${draft.name} silinsin mi?") },
            text = { Text("Bot ve sohbet geçmişi kalıcı olarak silinir.") },
            confirmButton = {
                TextButton(onClick = {
                    engine.deleteBot(draft.id)
                    confirmDelete = false
                    onDeleted()
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun TemplatePicker(
    modifier: Modifier = Modifier,
    onPick: (com.fatsar.hermes.core.logic.BotTemplate) -> Unit,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Nasıl bir bot istiyorsunuz?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Bir şablon seçin; sonraki adımda her ayrıntıyı değiştirebilirsiniz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Templates.all.forEach { template ->
            Surface(
                onClick = { onPick(template) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotAvatar(template.avatar, size = 42)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
