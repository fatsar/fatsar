@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fatsar.hermes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.core.model.ConnectionState
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.ConnectionBadge
import com.fatsar.hermes.ui.components.EmptyState
import com.fatsar.hermes.ui.components.InfoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    engine: BotEngine,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val data by engine.data.collectAsState()
    val connections by engine.connections.collectAsState()
    val agents by engine.agents.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") }
                },
                title = { Text("Sunucular", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { engine.refreshConnections() }) { Text("⟳") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agent ekle") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (data.servers.isEmpty()) {
            EmptyState(
                emoji = "🛰",
                title = "Bağlantı yok",
                subtitle = "VPS'inizde ya da bilgisayarınızda çalışan Hermes Agent'ı ekleyin.",
                actionLabel = "Bağlantı ekle",
                onAction = onAdd,
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(data.servers, key = { it.id }) { server ->
                val state = connections[server.id] ?: ConnectionState.UNKNOWN
                val info = agents[server.id]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(server.id) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    server.baseUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ConnectionBadge(state)
                        }
                        if (info != null && state == ConnectionState.ONLINE) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildString {
                                    append(info.name)
                                    if (info.version.isNotBlank()) append(" · v${info.version}")
                                    if (info.bots > 0) append(" · ${info.bots} kayıtlı bot")
                                    if (info.tools.isNotEmpty()) append(" · ${info.tools.size} araç")
                                    if (info.backendsReady.isNotEmpty()) {
                                        append("\nHazır LLM: ")
                                        append(info.backendsReady.joinToString(", "))
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state == ConnectionState.ONLINE) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { engine.importBotsFromAgent(server.id) }) {
                                    Text("Botları içe aktar")
                                }
                                TextButton(onClick = { engine.checkConnection(server.id) }) { Text("Yeniden dene") }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    "Hermes Agent = kendi sunucunuzda çalışan program. Botlar orada kayıtlıdır, " +
                        "araç kullanabilir ve telefon kapalıyken de zamanlanmış görevleri çalıştırır. " +
                        "LLM sağlayıcıları ve anahtarlar da agent'ta tanımlıdır.",
                )
            }
        }
    }
}
