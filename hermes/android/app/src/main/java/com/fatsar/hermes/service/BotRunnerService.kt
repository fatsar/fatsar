package com.fatsar.hermes.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.fatsar.hermes.HermesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Zamanlanmış botlar agent'ta (sunucuda) çalışır; telefon kapalıyken bile.
 * Bu servis yalnızca o sonuçları düzenli aralıklarla getirir: sohbete ekler
 * ve bot bildirim istiyorsa bildirim gösterir.
 */
class BotRunnerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = HermesApplication.engineOf(this)
        val count = engine.scheduledBots().size
        val text = if (count > 0) {
            "$count zamanlanmış botun sonuçları izleniyor"
        } else {
            "Sonuç bekleniyor"
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.SERVICE_NOTIFICATION_ID,
                Notifications.serviceNotification(this, text),
                type,
            )
        }

        if (loop == null) {
            loop = scope.launch {
                while (isActive) {
                    runCatching { engine.pollScheduledResults() }
                    delay(engine.pollIntervalMillis())
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        loop = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BotRunnerService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BotRunnerService::class.java)) }
        }
    }
}
