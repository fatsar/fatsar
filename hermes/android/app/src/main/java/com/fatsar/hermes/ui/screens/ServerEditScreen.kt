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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.logic.Urls
import com.fatsar.hermes.core.model.ConnectionState
import com.fatsar.hermes.core.model.ServerKind
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.BannerTone
import com.fatsar.hermes.ui.components.ChipRow
import com.fatsar.hermes.ui.components.ConnectionBadge
import com.fatsar.hermes.ui.components.HermesField
import com.fatsar.hermes.ui.components.InfoBanner
import com.fatsar.hermes.ui.components.SectionTitle

private val OPENAI_PRESETS = listOf(
    Triple("xAI (Grok)", "https://api.x.ai/v1", "grok-3"),
    Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    Triple("OpenRouter", "https://openrouter.ai/api/v1", ""),
    Triple("Groq", "https://api.groq.com/openai/v1", ""),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    engine: BotEngine,
    serverId: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val data by engine.data.collectAsState()
    val connections by engine.connections.collectAsState()
    val clipboard = LocalClipboardManager.current
    val existing = remember(serverId, data.servers) { data.servers.firstOrNull { it.id == serverId } }

    var draft by remember {
        mutableStateOf(
            existing ?: ServerProfile(
                id = Ids.newId("srv", System.currentTimeMillis()),
                name = "",
                kind = ServerKind.HERMES,
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
                    Text(if (existing == null) "Bağlantı ekle" else "Bağlantıyı düzenle", fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    TextButton(
                        onClick = {
                            engine.upsertServer(
                                draft.copy(
                                    name = draft.name.ifBlank {
                                        Urls.hostOf(Urls.normalizeBase(draft.baseUrl)) ?: "Sunucu"
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
                        if (text.contains("HERMES1:", ignoreCase = true)) {
                            val added = engine.addFromPairing(text)
                            if (added) {
                                engine.notice("Sunucu eklendi.")
                                onBack()
                            }
                        }
                    },
                    placeholder = "HERMES1:… (agent açılışta yazdırır)",
                    supporting = "Sunucuda hermes_agent.py çalıştırınca ekrana yazılan kodu yapıştırın.",
                    singleLine = false,
                    minLines = 2,
                )
                Row {
                    TextButton(onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        if (text.isBlank()) {
                            engine.notice("Pano boş.")
                        } else if (engine.addFromPairing(text)) {
                            engine.notice("Sunucu panodan eklendi.")
                            onBack()
                        } else {
                            engine.notice("Panoda geçerli bir eşleştirme kodu yok.")
                        }
                    }) { Text("Panodan yapıştır") }
                }
            }

            SectionTitle("Bağlantı türü")
            ChipRow(
                options = listOf(
                    ServerKind.HERMES.name to "Hermes Agent",
                    ServerKind.OPENAI.name to "API (Grok…)",
                    ServerKind.OLLAMA.name to "Ollama",
                ),
                selected = draft.kind.name,
                onSelect = { draft = draft.copy(kind = ServerKind.valueOf(it)) },
            )
            Spacer(Modifier.height(8.dp))
            InfoBanner(
                when (draft.kind) {
                    ServerKind.HERMES ->
                        "Kendi sunucunuzdaki agent: botlar sunucuda kayıtlı olur, araç kullanabilir, " +
                            "zamanlanmış görevler telefon kapalıyken de çalışır."
                    ServerKind.OPENAI ->
                        "Doğrudan model API'si: kurulum gerektirmez, telefondan sohbet edersiniz. " +
                            "Araçlar ve sunucu tarafı zamanlama çalışmaz."
                    ServerKind.OLLAMA ->
                        "Kendi makinenizdeki Ollama: ücretsiz yerel modeller. Telefonun aynı ağda " +
                            "olması ya da adresin dışarı açık olması gerekir."
                },
            )

            if (draft.kind == ServerKind.OPENAI) {
                SectionTitle("Hazır sağlayıcılar")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OPENAI_PRESETS.forEach { (label, url, _) ->
                        Surface(
                            onClick = {
                                draft = draft.copy(
                                    baseUrl = url,
                                    name = draft.name.ifBlank { label },
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (draft.baseUrl == url) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            SectionTitle("Adres ve anahtar")
            HermesField(
                label = "Görünen ad",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                placeholder = "ör. Hostinger VPS",
            )
            Spacer(Modifier.height(10.dp))
            HermesField(
                label = "Adres",
                value = draft.baseUrl,
                onValueChange = { draft = draft.copy(baseUrl = it) },
                placeholder = Urls.placeholderFor(draft.kind),
                supporting = "http:// yazmazsanız otomatik eklenir.",
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.token,
                onValueChange = { draft = draft.copy(token = it.trim()) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        when (draft.kind) {
                            ServerKind.HERMES -> "Agent token"
                            ServerKind.OPENAI -> "API anahtarı"
                            ServerKind.OLLAMA -> "Token (genelde gerekmez)"
                        },
                    )
                },
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
                        engine.upsertServer(draft.copy(name = draft.name.ifBlank { "Sunucu" }))
                        engine.checkConnection(draft.id)
                    },
                    enabled = draft.baseUrl.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Bağlantıyı sına") }
                Spacer(Modifier.width(12.dp))
                ConnectionBadge(connections[draft.id] ?: ConnectionState.UNKNOWN)
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
            text = { Text("Bu bağlantıyı kullanan botlar başka bir sunucuya taşınır.") },
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
