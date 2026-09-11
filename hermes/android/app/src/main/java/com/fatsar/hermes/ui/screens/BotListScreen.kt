package com.fatsar.hermes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.BotStatus
import com.fatsar.hermes.core.model.ConnectionState
import com.fatsar.hermes.core.model.ScheduleMode
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.BotAvatar
import com.fatsar.hermes.ui.components.ConnectionBadge
import com.fatsar.hermes.ui.components.EmptyState
import com.fatsar.hermes.ui.components.relativeTime
import com.fatsar.hermes.ui.components.statusColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotListScreen(
    engine: BotEngine,
    modifier: Modifier = Modifier,
    onOpenChat: (String) -> Unit,
    onNewBot: () -> Unit,
    onEditBot: (String) -> Unit,
    onServers: () -> Unit,
    onSettings: () -> Unit,
) {
    val data by engine.data.collectAsState()
    val statuses by engine.status.collectAsState()
    val chats by engine.chats.collectAsState()
    val connections by engine.connections.collectAsState()
    var confirmDelete by remember { mutableStateOf<BotSpec?>(null) }

    LaunchedEffect(data.bots) {
        data.bots.forEach { engine.ensureChatLoaded(it.id) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Botlarım", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${data.bots.size} bot · ${data.servers.size} sunucu",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { engine.refreshConnections() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                    }
                    IconButton(onClick = onServers) { Text("🛰", style = MaterialTheme.typography.titleMedium) }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewBot,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni bot") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (data.servers.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(data.servers, key = { it.id }) { server ->
                        Surface(
                            onClick = onServers,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    server.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                ConnectionBadge(connections[server.id] ?: ConnectionState.UNKNOWN)
                            }
                        }
                    }
                }
            }

            if (data.bots.isEmpty()) {
                EmptyState(
                    emoji = "🤖",
                    title = "Henüz botunuz yok",
                    subtitle = "Hazır şablonlardan seçerek saniyeler içinde ilk botunuzu oluşturun: " +
                        "sohbet botu, sunucu bekçisi, haber özetleyici…",
                    actionLabel = "İlk botu oluştur",
                    onAction = onNewBot,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(data.bots, key = { it.id }) { bot ->
                        BotCard(
                            bot = bot,
                            serverName = engine.serverFor(bot)?.name.orEmpty(),
                            status = statuses[bot.id] ?: BotStatus.IDLE,
                            lastMessage = chats[bot.id]?.lastOrNull(),
                            onOpen = { onOpenChat(bot.id) },
                            onEdit = { onEditBot(bot.id) },
                            onDuplicate = { engine.duplicateBot(bot.id) },
                            onDelete = { confirmDelete = bot },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { bot ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("${bot.name} silinsin mi?") },
            text = { Text("Botun tüm sohbet geçmişi de silinir. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    engine.deleteBot(bot.id)
                    confirmDelete = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun BotCard(
    bot: BotSpec,
    serverName: String,
    status: BotStatus,
    lastMessage: com.fatsar.hermes.core.model.ChatMessage?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            BotAvatar(bot.avatar, badge = statusColor(if (bot.enabled) status else BotStatus.DISABLED))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bot.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (status == BotStatus.RUNNING) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "çalışıyor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        bot.model.ifBlank { null },
                        serverName.ifBlank { null },
                    ).joinToString(" · ").ifBlank { "model seçilmedi" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lastMessage != null && lastMessage.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        lastMessage.content.replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (bot.schedule.mode != ScheduleMode.OFF && bot.schedule.prompt.isNotBlank()) {
                        Tag(
                            when (bot.schedule.mode) {
                                ScheduleMode.INTERVAL -> "⏰ ${bot.schedule.everyMinutes} dk"
                                ScheduleMode.DAILY -> "⏰ %02d:%02d".format(bot.schedule.atHour, bot.schedule.atMinute)
                                else -> ""
                            },
                        )
                    }
                    if (bot.tools.isNotEmpty()) Tag("🔧 ${bot.tools.size} araç")
                    if (!bot.enabled) Tag("duraklatıldı")
                    lastMessage?.ts?.let { ts ->
                        if (ts > 0) {
                            Text(
                                relativeTime(ts),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Seçenekler")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Düzenle") }, onClick = {
                        menu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Kopyala") }, onClick = {
                        menu = false
                        onDuplicate()
                    })
                    DropdownMenuItem(text = { Text("Sil") }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    if (text.isBlank()) return
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
