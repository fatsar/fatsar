package com.fatsar.hermes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.fatsar.hermes.ui.HermesRoot
import com.fatsar.hermes.ui.theme.HermesTheme

class MainActivity : ComponentActivity() {

    private val startBotId = mutableStateOf<String?>(null)
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consume(intent)
        setContent {
            HermesTheme {
                HermesRoot(
                    engine = HermesApplication.engineOf(this),
                    startBotId = startBotId,
                    sharedText = sharedText,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    /** Bildirimden, paylaş menüsünden veya hermes:// bağlantısından gelen veriyi alır. */
    private fun consume(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(EXTRA_BOT_ID)?.let { startBotId.value = it }

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText.value = it }
        }

        val data = intent.data?.toString()
        if (!data.isNullOrBlank()) {
            val engine = HermesApplication.engineOf(this)
            if (engine.addFromPairing(data)) {
                engine.notice("Sunucu eşleştirme kodundan eklendi.")
            }
        }
    }

    companion object {
        const val EXTRA_BOT_ID = "bot_id"
    }
}
