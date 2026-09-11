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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.InfoBanner
import com.fatsar.hermes.ui.components.MonoText
import com.fatsar.hermes.ui.components.SectionTitle

private const val AGENT_URL =
    "https://raw.githubusercontent.com/fatsar/fatsar/main/hermes/agent/hermes_agent.py"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    engine: BotEngine,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val data by engine.data.collectAsState()
    val clipboard = LocalClipboardManager.current
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    val localScheduled = engine.locallyScheduledBots().size

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri") }
                },
                title = { Text("Ayarlar", fontWeight = FontWeight.SemiBold) },
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
            SectionTitle("Arka plan")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = data.backgroundEnabled,
                    onCheckedChange = {
                        engine.setBackgroundEnabled(it)
                        if (it) onRequestNotifications()
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Zamanlanmış botları telefonda çalıştır", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (localScheduled > 0) {
                            "$localScheduled bot telefonda zamanlanmış"
                        } else {
                            "Telefonda zamanlanmış bot yok"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoBanner(
                "Hermes Agent'a bağlı botların zamanlaması zaten sunucuda çalışır; " +
                    "bu ayar yalnızca doğrudan API'ye bağlı botlar içindir.",
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRequestNotifications) { Text("Bildirim iznini kontrol et") }

            SectionTitle("Sunucu kurulumu")
            Text(
                "Hostinger/Contabo gibi bir VPS'te ya da kendi bilgisayarınızda:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                MonoText(
                    "curl -fsSL $AGENT_URL -o hermes_agent.py\npython3 hermes_agent.py --allow-shell",
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = {
                clipboard.setText(
                    AnnotatedString("curl -fsSL $AGENT_URL -o hermes_agent.py && python3 hermes_agent.py"),
                )
                engine.notice("Komut panoya kopyalandı.")
            }) { Text("Komutu kopyala") }

            SectionTitle("Yedekleme")
            Text(
                "Sunucular, botlar ve ayarlar tek bir JSON metnine aktarılır. " +
                    "Anahtarlar da bu metne dâhildir; güvenli bir yerde saklayın.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(engine.exportSettings()))
                        engine.notice("Ayarlar panoya kopyalandı.")
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Panoya aktar") }
                OutlinedButton(onClick = { showImport = !showImport }, shape = RoundedCornerShape(14.dp)) {
                    Text("Geri yükle")
                }
            }
            if (showImport) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Yedek JSON") },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val source = importText.ifBlank { clipboard.getText()?.text.orEmpty() }
                            if (engine.importSettings(source)) {
                                engine.notice("Yedek geri yüklendi.")
                                showImport = false
                                importText = ""
                            } else {
                                engine.notice("JSON okunamadı.")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Yükle") }
                    TextButton(onClick = { importText = clipboard.getText()?.text.orEmpty() }) {
                        Text("Panodan al")
                    }
                }
            }

            SectionTitle("Hakkında")
            Text("Hermes Bot Konsol " + com.fatsar.hermes.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Kendi sunucunuzdaki botları yöneten açık kaynak istemci.\n" +
                    "Kaynak kod: github.com/fatsar/fatsar → hermes/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${data.bots.size} bot · ${data.servers.size} bağlantı",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
