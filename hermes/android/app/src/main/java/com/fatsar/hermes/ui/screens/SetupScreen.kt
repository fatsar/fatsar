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
import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.model.ServerKind
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.ui.components.HermesField
import com.fatsar.hermes.ui.components.InfoBanner
import com.fatsar.hermes.ui.components.MonoText

/** İlk açılış: kullanıcıyı üç yoldan biriyle çalışır duruma getirir. */
@Composable
fun SetupScreen(
    engine: BotEngine,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(0) } // 0 = seçim, 1 = agent, 2 = api
    val clipboard = LocalClipboardManager.current
    var pairing by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("https://api.x.ai/v1") }

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
        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> {
                SetupCard(
                    emoji = "🛰",
                    title = "Sunucumda Hermes Agent var",
                    subtitle = "VPS veya bilgisayarımda agent çalışıyor; eşleştirme kodunu gireceğim.",
                    onClick = { step = 1 },
                )
                SetupCard(
                    emoji = "🔑",
                    title = "Doğrudan API kullanacağım",
                    subtitle = "xAI Grok / OpenAI / OpenRouter anahtarımla hemen sohbete başlayayım.",
                    onClick = { step = 2 },
                )
                SetupCard(
                    emoji = "👀",
                    title = "Önce bir bakayım",
                    subtitle = "Bağlantıyı sonra ekleyeceğim.",
                    onClick = onDone,
                )
            }

            1 -> {
                Text("Sunucuda şunu çalıştırın:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                    MonoText(
                        "curl -fsSL https://github.com/fatsar/fatsar/releases/download/hermes-v1.0.0/hermes_agent.py \\\n" +
                            "  -o hermes_agent.py\n" +
                            "python3 hermes_agent.py",
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Açılışta ekrana yazılan HERMES1:… kodunu buraya yapıştırın.",
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
                    TextButton(onClick = { step = 0 }) { Text("Geri") }
                }
            }

            2 -> {
                Text("API bilgileri", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                HermesField(
                    label = "API adresi",
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    placeholder = "https://api.x.ai/v1",
                    supporting = "xAI: https://api.x.ai/v1 · OpenAI: https://api.openai.com/v1",
                )
                Spacer(Modifier.height(10.dp))
                HermesField(
                    label = "API anahtarı",
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    placeholder = "xai-… / sk-…",
                )
                Spacer(Modifier.height(12.dp))
                InfoBanner("Anahtar yalnızca telefonunuzda saklanır, başka hiçbir yere gönderilmez.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            engine.upsertServer(
                                ServerProfile(
                                    id = Ids.newId("srv", now),
                                    name = if (apiUrl.contains("x.ai")) "xAI Grok" else "API",
                                    kind = ServerKind.OPENAI,
                                    baseUrl = apiUrl,
                                    token = apiKey,
                                    createdAt = now,
                                ),
                            )
                            engine.notice("Bağlantı eklendi 🎉")
                            onDone()
                        },
                        enabled = apiUrl.isNotBlank() && apiKey.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Kaydet") }
                    TextButton(onClick = { step = 0 }) { Text("Geri") }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}
