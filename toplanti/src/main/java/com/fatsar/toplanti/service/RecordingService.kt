package com.fatsar.toplanti.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.VoskModelManager
import com.fatsar.toplanti.audio.WavWriter
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.ui.RecordingActivity
import java.io.File
import kotlin.math.sqrt

/**
 * Ön plan ses kayıt servisi (FR-004): ekran kilitli veya uygulama arka
 * plandayken kayıt devam eder; kalıcı bildirim gösterilir (AC-002).
 * Ses 16 kHz mono WAV olarak uygulamaya özel depolamaya yazılır ve
 * başlık periyodik güncellenir; beklenmedik kapanmada veri kurtarılabilir.
 */
class RecordingService : Service() {

    interface Listener {
        fun onTick(elapsedMs: Long, level: Int)
        fun onFinished(meetingId: String, durationMs: Long)
    }

    inner class LocalBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = LocalBinder()
    var listener: Listener? = null

    var meetingId: String = ""
        private set
    @Volatile var isPaused = false
        private set
    @Volatile var isRecording = false
        private set

    @Volatile private var elapsedMs = 0L
    @Volatile private var stopRequested = false
    private var thread: Thread? = null

    val currentElapsedMs: Long get() = elapsedMs

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_MEETING_ID) ?: return START_NOT_STICKY
                if (!isRecording) startRecording(id)
            }
            ACTION_STOP -> finishRecording()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // İzin, kayıt ekranı açılmadan önce alınır
    private fun startRecording(id: String) {
        meetingId = id
        isRecording = true
        stopRequested = false
        elapsedMs = 0L

        createChannel()
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )

        val repo = MeetingRepository(applicationContext)
        val outFile = File(repo.meetingDir(id), AUDIO_FILE_NAME)

        thread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf, 8192)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2
            )
            val writer = WavWriter(outFile, SAMPLE_RATE)
            val buf = ByteArray(bufSize)
            var lastSync = System.currentTimeMillis()
            try {
                recorder.startRecording()
                while (!stopRequested) {
                    if (isPaused) {
                        Thread.sleep(50)
                        continue
                    }
                    val n = recorder.read(buf, 0, buf.size)
                    if (n > 0) {
                        writer.write(buf, n)
                        elapsedMs += (n / 2) * 1000L / SAMPLE_RATE
                        listener?.onTick(elapsedMs, level(buf, n))
                        val now = System.currentTimeMillis()
                        if (now - lastSync > 5000) {
                            writer.sync()
                            lastSync = now
                        }
                    }
                }
            } catch (_: InterruptedException) {
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                writer.close()
            }
            val duration = elapsedMs
            isRecording = false
            finalizeMeeting(duration)
            listener?.onFinished(meetingId, duration)
            stopSelf()
        }.apply { start() }
    }

    /**
     * Toplantı metasını günceller ve işlemeyi başlatır. Aktivite kapalı olsa
     * bile çalışır; model kurulu değilse toplantı, detay ekranından "Yeniden
     * dene" ile kurtarılabilecek şekilde FAILED olarak işaretlenir.
     */
    private fun finalizeMeeting(duration: Long) {
        val repo = MeetingRepository(applicationContext)
        val meeting = repo.loadMeeting(meetingId) ?: return
        meeting.endedAt = System.currentTimeMillis()
        meeting.durationMs = duration
        meeting.audioFileName = AUDIO_FILE_NAME
        val manager = VoskModelManager(applicationContext)
        val modelReady = VoskModelManager.requiredLanguages(meeting.language).all { manager.isReady(it) }
        if (modelReady) {
            meeting.status = MeetingStatus.PROCESSING
        } else {
            meeting.status = MeetingStatus.FAILED
            meeting.errorMessage = getString(R.string.error_model_missing)
        }
        repo.saveMeeting(meeting)
        if (modelReady) ProcessingService.start(this, meetingId)
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun finishRecording() {
        stopRequested = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun level(buf: ByteArray, len: Int): Int {
        var sum = 0.0
        var i = 0
        while (i + 1 < len) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        val rms = sqrt(sum / (len / 2))
        return ((rms / 32768.0) * 300).toInt().coerceIn(0, 100)
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_recording),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, RecordingActivity::class.java)
            .putExtra(EXTRA_MEETING_ID, meetingId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notif_recording_title))
            .setContentText(getString(R.string.notif_recording_text))
            .setOngoing(true)
            .setContentIntent(pi)
            .setUsesChronometer(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.fatsar.toplanti.action.START_RECORDING"
        const val ACTION_STOP = "com.fatsar.toplanti.action.STOP_RECORDING"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val AUDIO_FILE_NAME = "recording.wav"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 101
    }
}
