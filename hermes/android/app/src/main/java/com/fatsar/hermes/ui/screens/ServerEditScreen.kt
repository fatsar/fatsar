@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fatsar.hermes.ui.screens

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.logic.Urls
import com.fatsar.hermes.core.model.ConnectionState
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.BannerTone
import com.fatsar.hermes.ui.components.ConnectionBadge
import com.fatsar.hermes.ui.components.HermesField
import com.fatsar.hermes.ui.components.InfoBanner
import com.fatsar.hermes.ui.components.SectionTitle

/** Hermes Agent bağlantısı: adres + token. Model sağlayıcıları agent'ta tanımlıdır. */
@Composable
fun ServerEditScreen(
    engine: BotEngine,
    serverId: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val data by engine.data.collectAsState()
    val connections by engine.connections.collectAsState()
    val backends by engine.backends.collectAsState()
    val clipboard = LocalClipboardManager.current
    val existing = remember(serverId, data.servers) { data.servers.firstOrNull { it.id == serverId } }

    var draft by remember {
        mutableStateOf(
            existing ?: ServerProfile(
                id = Ids.newId("srv", System.currentTimeMillis()),
                name = "",
                baseUrl = "",
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
    var pairingInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") }
                },
                title = {
                    Text(
                        if (existing == null) "Agent bağlantısı ekle" else "Bağlantıyı düzenle",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            engine.upsertServer(
                                draft.copy(
                                    name = draft.name.ifBlank {
                                        Urls.hostOf(Urls.normalizeBase(draft.baseUrl)) ?: "Hermes Agent"
                                    },
                                ),
                            )
                            onBack()
                        },
                        enabled = draft.baseUrl.isNotBlank(),
                    ) { Text("Kaydet") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            if (existing == null) {
                SectionTitle("Hızlı kurulum")
                HermesField(
                    label = "Eşleştirme kodu",
                    value = pairingInput,
                    onValueChange = { text ->
                        pairingInput = text
                        if (text.contains("HERMES1:", ignoreCase = true) && engine.addFromPairing(text)) {
                            engine.notice("Sunucu eklendi.")
                            onBack()
                        }
                    },
                    placeholder = "HERMES1:… (agent açılışta yazdırır)",
                    supporting = "Adres ve token kodun içindedir.",
                    singleLine = false,
                    minLines = 2,
                )
                TextButton(onClick = {
                    val text = clipboard.getText()?.text.orEmpty()
                    when {
                        text.isBlank() -> engine.notice("Pano boş.")
                        engine.addFromPairing(text) -> {
                            engine.notice("Sunucu panodan eklendi.")
                            onBack()
                        }
                        else -> engine.notice("Panoda geçerli bir eşleştirme kodu yok.")
                    }
                }) { Text("Panodan yapıştır") }
            }

            SectionTitle("Adres ve token")
            HermesField(
                label = "Görünen ad",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                placeholder = "ör. Hostinger VPS",
            )
            Spacer(Modifier.height(10.dp))
            HermesField(
                label = "Agent adresi",
                value = draft.baseUrl,
                onValueChange = { draft = draft.copy(baseUrl = it) },
                placeholder = Urls.ADDRESS_PLACEHOLDER,
                supporting = "http:// yazmazsanız otomatik eklenir.",
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.token,
                onValueChange = { draft = draft.copy(token = it.trim()) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Agent token") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "gizle" else "göster", style = MaterialTheme.typography.labelSmall)
                    }
                },
                shape = RoundedCornerShape(14.dp),
            )

            if (Urls.isInsecurePublic(Urls.normalizeBase(draft.baseUrl))) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    "Bu adres internete açık ve şifresiz (http). Token ağda düz metin gider. " +
                        "VPS'te Caddy/Nginx ile HTTPS kullanmanız önerilir.",
                    tone = BannerTone.WARNING,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        engine.upsertServer(draft.copy(name = draft.name.ifBlank { "Hermes Agent" }))
                        engine.checkConnection(draft.id)
                    },
                    enabled = draft.baseUrl.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Bağlantıyı sına") }
                Spacer(Modifier.width(12.dp))
                ConnectionBadge(connections[draft.id] ?: ConnectionState.UNKNOWN)
            }

            // Bağlantı kurulduysa agent'taki LLM sağlayıcıları burada görünür.
            val list = backends[draft.id].orEmpty()
            if (list.isNotEmpty()) {
                SectionTitle("Agent'taki LLM sağlayıcıları")
                list.forEach { backend ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (backend.ready) "✅" else "⚪")
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                backend.label + if (backend.isDefault) "  · varsayılan" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val note = when {
                                backend.note.isNotBlank() -> backend.note
                                !backend.supportsTools -> "Araç kullanımı desteklenmiyor"
                                else -> ""
                            }
                            if (note.isNotBlank()) {
                                Text(
                                    note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                InfoBanner(
                    "Hazır olmayan bir sağlayıcıyı kullanmak için anahtarını sunucuda tanımlayın:\n" +
                        "hermes_agent.py --set backends.<ad>.api_key=… ardından servisi yeniden başlatın.",
                )
            }

            if (existing != null) {
                Spacer(Modifier.height(28.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Bağlantıyı sil") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Bağlantı silinsin mi?") },
            text = { Text("Bu bağlantıyı kullanan botlar varsa başka bir sunucuya taşınır.") },
            confirmButton = {
                TextButton(onClick = {
                    engine.deleteServer(draft.id)
                    confirmDelete = false
                    onBack()
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}
