package com.fatsar.hermes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.core.model.BotStatus
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.Role
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.BotAvatar
import com.fatsar.hermes.ui.components.MonoText
import com.fatsar.hermes.ui.components.TypingCursor
import com.fatsar.hermes.ui.components.clockTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    engine: BotEngine,
    botId: String,
    modifier: Modifier = Modifier,
    sharedText: MutableState<String?>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val data by engine.data.collectAsState()
    val chats by engine.chats.collectAsState()
    val statuses by engine.status.collectAsState()
    val usages by engine.usage.collectAsState()
    val bot = data.bots.firstOrNull { it.id == botId }
    val messages = chats[botId].orEmpty()
    val status = statuses[botId] ?: BotStatus.IDLE
    val running = status == BotStatus.RUNNING
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(botId) {
        engine.ensureChatLoaded(botId)
        engine.setActiveBot(botId)
    }

    // Başka uygulamadan paylaşılan metin giriş kutusuna düşer.
    LaunchedEffect(sharedText.value) {
        sharedText.value?.let {
            input = it
            sharedText.value = null
        }
    }

    // Akış sürerken alta kaydır (her 40 karakterde bir).
    val scrollKey = messages.size to (messages.lastOrNull()?.content?.length ?: 0) / 40
    LaunchedEffect(scrollKey) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    if (bot == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BotAvatar(bot.avatar, size = 34)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                bot.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    running -> "yanıtlıyor…"
                                    status == BotStatus.ERROR -> "son çalışmada hata"
                                    else -> listOfNotNull(
                                        bot.model.ifBlank { null },
                                        usages[botId],
                                    ).joinToString(" · ").ifBlank { "hazır" }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status == BotStatus.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Seçenekler")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Bot ayarları") }, onClick = {
                                menu = false
                                onEdit()
                            })
                            DropdownMenuItem(text = { Text("Sohbeti kopyala") }, onClick = {
                                menu = false
                                clipboard.setText(
                                    AnnotatedString(
                                        messages.joinToString("\n\n") {
                                            "${if (it.role == Role.USER) "Ben" else bot.name}: ${it.content}"
                                        },
                                    ),
                                )
                                engine.notice("Sohbet panoya kopyalandı.")
                            })
                            DropdownMenuItem(text = { Text("Sohbeti temizle") }, onClick = {
                                menu = false
                                confirmClear = true
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding(),
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BotAvatar(bot.avatar, size = 72)
                    Spacer(Modifier.height(12.dp))
                    Text(bot.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bot.systemPrompt.take(160).ifBlank { "Bu bot için bir görev tanımı yazılmadı." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    listOf("Merhaba, kendini tanıt", "Neler yapabilirsin?", bot.schedule.prompt)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { suggestion ->
                            Surface(
                                onClick = { input = suggestion },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                Text(
                                    suggestion,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message, bot.avatar)
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mesaj yazın…") },
                        maxLines = 5,
                        shape = RoundedCornerShape(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (running) {
                                engine.stop(botId)
                            } else {
                                val text = input
                                input = ""
                                engine.send(botId, text)
                            }
                        },
                        enabled = running || input.isNotBlank(),
                        modifier = Modifier.size(52.dp),
                        colors = if (running) {
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        } else {
                            IconButtonDefaults.filledIconButtonColors()
                        },
                    ) {
                        Icon(
                            if (running) Icons.Default.Close else Icons.Default.Send,
                            contentDescription = if (running) "Durdur" else "Gönder",
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Sohbet temizlensin mi?") },
            text = { Text("Bu botun tüm mesaj geçmişi silinir; bot tanımı korunur.") },
            confirmButton = {
                TextButton(onClick = {
                    engine.clearChat(botId)
                    confirmClear = false
                }) { Text("Temizle", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, botAvatar: String) {
    when (message.role) {
        Role.TOOL -> ToolBubble(message)
        Role.USER -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    SelectionContainer {
                        Text(
                            message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        clockTime(message.ts),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }

        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(botAvatar.ifBlank { "🤖" }, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
            Surface(
                color = if (message.error) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    SelectionContainer {
                        Text(
                            message.content.ifBlank { if (message.streaming) "" else "(boş yanıt)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.error) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (message.streaming) TypingCursor()
                }
            }
        }
    }
}

@Composable
private fun ToolBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(start = 26.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    "🔧 ${message.toolName.orEmpty()}" + if (message.streaming) " çalışıyor…" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    MonoText(message.content, modifier = Modifier.widthIn(max = 300.dp))
                }
            }
        }
    }
}
