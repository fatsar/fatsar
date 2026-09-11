package com.fatsar.hermes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fatsar.hermes.MainActivity
import com.fatsar.hermes.R

object Notifications {

    const val CHANNEL_STATUS = "hermes_status"
    const val CHANNEL_RESULTS = "hermes_results"
    const val SERVICE_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Arka planda çalışan botların durumu" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                context.getString(R.string.notification_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Zamanlanmış bot çalışmalarının sonuçları" },
        )
    }

    private fun openAppIntent(context: Context, botId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (botId != null) putExtra(MainActivity.EXTRA_BOT_ID, botId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, botId?.hashCode() ?: 0, intent, flags)
    }

    fun serviceNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setContentTitle("Hermes Bot")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context, null))
            .build()

    /** Zamanlanmış bir çalışma bittiğinde sonucu bildirir. */
    fun postResult(context: Context, botId: String, botName: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setContentTitle(botName)
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1500)))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, botId))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(botId.hashCode(), notification)
        }
    }
}
