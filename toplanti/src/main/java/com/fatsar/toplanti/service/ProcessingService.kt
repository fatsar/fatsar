package com.fatsar.toplanti.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.Transcriber
import com.fatsar.toplanti.asr.VoskModelManager
import com.fatsar.toplanti.audio.AudioFileDecoder
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.model.Speaker
import com.fatsar.toplanti.nlp.MeetingAnalyzer
import com.fatsar.toplanti.ui.MeetingDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Kaydedilen/içe aktarılan sesi arka planda işler: çözme → konuşma tanıma →
 * temizleme → analiz. İlerleme bildirimi gösterir; bitince veya hata olunca
 * kullanıcıyı bilgilendirir (PRD 10.4, FR-025 hata geri bildirimi).
 * Tüm işleme cihaz üzerindedir; ses hiçbir sunucuya gönderilmez.
 */
class ProcessingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ArrayDeque<String>()
    private var running = false
    @Volatile private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val meetingId = intent?.getStringExtra(EXTRA_MEETING_ID) ?: return START_NOT_STICKY
        queue.addLast(meetingId)
        if (!running) processNext()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun processNext() {
        val meetingId = queue.removeFirstOrNull()
        if (meetingId == null) {
            running = false
            stopSelf()
            return
        }
        running = true
        lastPercent = -1
        createChannels()
        ServiceCompat.startForeground(
            this, NOTIF_ID, progressNotification(0),
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        scope.launch {
            val repo = MeetingRepository(applicationContext)
            val meeting = repo.loadMeeting(meetingId)
            if (meeting == null) {
                processNext()
                return@launch
            }
            try {
                meeting.status = MeetingStatus.PROCESSING
                meeting.errorMessage = ""
                repo.saveMeeting(meeting)

                val modelDir = VoskModelManager(applicationContext).installedModelDir()
                    ?: throw IllegalStateException(getString(R.string.error_model_missing))
                val audio = repo.audioFile(meeting)
                    ?: throw IllegalStateException(getString(R.string.error_audio_missing))

                // 1) Çözme + tanıma (ilerleme 0..85)
                val transcriber = Transcriber(modelDir)
                val segments = try {
                    AudioFileDecoder(audio).decode(
                        onPcm = { pcm -> transcriber.accept(pcm, pcm.size) },
                        onProgress = { p -> updateProgress(p * 85 / 100) }
                    )
                    transcriber.finish()
                } finally {
                    transcriber.close()
                }

                // 2) Konuşmacı ataması: otomatik ayrım yapılamadığında tek konuşmacı
                //    sunulur; kullanıcı elle etiketleyebilir (PRD hata durumu tablosu)
                if (meeting.speakers.isEmpty()) {
                    meeting.speakers.add(Speaker(label = getString(R.string.speaker_default, 1)))
                }
                val defaultSpeaker = meeting.speakers.first()
                segments.forEach { it.speakerId = defaultSpeaker.id }
                updateProgress(90)

                // 3) Analiz: özet, notlar, kararlar, görevler, başlık önerisi
                val analysis = MeetingAnalyzer.analyze(segments, meeting.meetingDate)
                meeting.shortSummary = analysis.shortSummary
                meeting.detailedSummary = analysis.detailedSummary
                meeting.summaryConfidence = analysis.summaryConfidence
                meeting.insights.clear()
                meeting.insights.addAll(analysis.insights)
                meeting.tasks.clear()
                meeting.tasks.addAll(analysis.tasks)
                meeting.suggestedTitle = analysis.suggestedTitle
                meeting.status = MeetingStatus.REVIEW

                repo.saveSegments(meeting.id, segments)
                repo.saveMeeting(meeting)
                updateProgress(100)
                notifyDone(meeting.id, meeting.displayTitle(getString(R.string.untitled_meeting)))
            } catch (e: Exception) {
                meeting.status = MeetingStatus.FAILED
                meeting.errorMessage = e.message ?: e.javaClass.simpleName
                repo.saveMeeting(meeting)
                notifyFailed(meeting.id)
            }
            processNext()
        }
    }

    private fun updateProgress(percent: Int) {
        if (percent == lastPercent) return
        lastPercent = percent
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, progressNotification(percent))
    }

    private fun progressNotification(percent: Int): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_process)
            .setContentTitle(getString(R.string.notif_processing_title))
            .setContentText(getString(R.string.notif_processing_text, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .build()

    private fun notifyDone(meetingId: String, title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Kilit ekranında hassas içerik (başlık) gizlenir (PRD 13.1)
        nm.notify(
            meetingId.hashCode(),
            NotificationCompat.Builder(this, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_done)
                .setContentTitle(getString(R.string.notif_done_title))
                .setContentText(title)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(detailIntent(meetingId))
                .build()
        )
    }

    private fun notifyFailed(meetingId: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            meetingId.hashCode(),
            NotificationCompat.Builder(this, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_error)
                .setContentTitle(getString(R.string.notif_failed_title))
                .setContentText(getString(R.string.notif_failed_text))
                .setAutoCancel(true)
                .setContentIntent(detailIntent(meetingId))
                .build()
        )
    }

    private fun detailIntent(meetingId: String): PendingIntent =
        PendingIntent.getActivity(
            this, meetingId.hashCode(),
            Intent(this, MeetingDetailActivity::class.java)
                .putExtra(EXTRA_MEETING_ID, meetingId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS, getString(R.string.notif_channel_processing),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT, getString(R.string.notif_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        const val EXTRA_MEETING_ID = "meeting_id"
        private const val CHANNEL_PROGRESS = "processing"
        private const val CHANNEL_RESULT = "results"
        private const val NOTIF_ID = 102

        fun start(context: Context, meetingId: String) {
            val intent = Intent(context, ProcessingService::class.java)
                .putExtra(EXTRA_MEETING_ID, meetingId)
            context.startForegroundService(intent)
        }
    }
}
