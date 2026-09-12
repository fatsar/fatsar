@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fatsar.hermes.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.HermesField
import com.fatsar.hermes.ui.components.InfoBanner
import com.fatsar.hermes.ui.components.MonoText

private const val AGENT_URL =
    "https://github.com/fatsar/fatsar/releases/download/hermes-v1.0.0/hermes_agent.py"

/**
 * İlk açılış. Uygulama yalnızca kendi sunucunuzdaki Hermes Agent'a bağlanır;
 * model sağlayıcıları ve anahtarlar orada tanımlıdır.
 */
@Composable
fun SetupScreen(
    engine: BotEngine,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    var showManual by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var pairing by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("🤖", fontSize = 52.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text(
            "Hermes Bot Konsol",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Kendi sunucunuzda çalışan botları telefonunuzdan yönetin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Text("1. Sunucunuzda agent'ı başlatın", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
            MonoText(
                "curl -fsSL $AGENT_URL \\\n" +
                    "  -o hermes_agent.py\n" +
                    "python3 hermes_agent.py",
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "VPS'te systemd ile kalıcı kurulum için install.sh betiğini kullanın (rehberde).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Text("2. Eşleştirme kodunu yapıştırın", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Agent açılışta HERMES1:… ile başlayan bir kod yazar; adres ve token onun içindedir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        HermesField(
            label = "Eşleştirme kodu",
            value = pairing,
            onValueChange = { pairing = it },
            placeholder = "HERMES1:…",
            singleLine = false,
            minLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (engine.addFromPairing(pairing)) {
                        engine.notice("Sunucu eklendi 🎉")
                        onDone()
                    } else {
                        engine.notice("Kod geçersiz görünüyor.")
                    }
                },
                enabled = pairing.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Bağlan") }
            OutlinedButton(
                onClick = { pairing = clipboard.getText()?.text.orEmpty() },
                shape = RoundedCornerShape(14.dp),
            ) { Text("Panodan al") }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { showManual = !showManual }) {
            Text(if (showManual) "Elle girişi gizle" else "Kodum yok, adresi elle gireyim")
        }
        if (showManual) {
            HermesField(
                label = "Agent adresi",
                value = address,
                onValueChange = { address = it },
                placeholder = "http://sunucu-ip:8713",
            )
            Spacer(Modifier.height(10.dp))
            HermesField(
                label = "Agent token",
                value = token,
                onValueChange = { token = it.trim() },
                placeholder = "agent açılışta yazar",
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    engine.upsertServer(
                        com.fatsar.hermes.core.model.ServerProfile(
                            id = com.fatsar.hermes.core.logic.Ids.newId("srv", now),
                            name = com.fatsar.hermes.core.logic.Urls.hostOf(address) ?: "Hermes Agent",
                            baseUrl = address,
                            token = token,
                            createdAt = now,
                        ),
                    )
                    engine.notice("Bağlantı eklendi 🎉")
                    onDone()
                },
                enabled = address.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Kaydet") }
        }

        Spacer(Modifier.height(20.dp))
        InfoBanner(
            "Model sağlayıcılar (Grok, OpenAI, Ollama, Claude…) ve API anahtarları " +
                "agent'ta tanımlanır. Uygulama yalnızca agent'ın hazır bildirdiği " +
                "sağlayıcılar arasından seçim yapar; anahtarlar telefona hiç inmez.",
        )

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Önce bir bakayım") }
    }
}
