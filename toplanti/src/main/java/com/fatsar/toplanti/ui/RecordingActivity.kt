package com.fatsar.toplanti.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.VoskModelManager
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.databinding.ActivityRecordingBinding
import com.fatsar.toplanti.model.Attachment
import com.fatsar.toplanti.model.AttachmentType
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.service.ProcessingService
import com.fatsar.toplanti.service.RecordingService
import com.fatsar.toplanti.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Canlı kayıt ekranı (PRD 10.3): süre, seviye göstergesi, duraklat/sürdür/
 * bitir (FR-003) ve toplantı sırasında görsel/video ekleme (FR-040).
 */
class RecordingActivity : AppCompatActivity(), RecordingService.Listener {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var repo: MeetingRepository
    private var meetingId: String = ""
    private var service: RecordingService? = null
    private var pendingCaptureFile: File? = null
    private var pendingCaptureType: AttachmentType = AttachmentType.IMAGE
    private var finishing = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as RecordingService.LocalBinder).service
            service?.listener = this@RecordingActivity
            updatePauseButton()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            onCaptureResult(ok)
        }

    private val captureVideo =
        registerForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
            onCaptureResult(ok)
        }

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { copyPicked(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = MeetingRepository(applicationContext)
        meetingId = intent.getStringExtra(EXTRA_MEETING_ID) ?: run { finish(); return }

        startForegroundService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_START)
                .putExtra(RecordingService.EXTRA_MEETING_ID, meetingId)
        )

        binding.btnPause.setOnClickListener {
            val s = service ?: return@setOnClickListener
            if (s.isPaused) s.resume() else s.pause()
            updatePauseButton()
        }
        binding.btnFinish.setOnClickListener { confirmFinish() }
        binding.btnPhoto.setOnClickListener { capture(AttachmentType.IMAGE) }
        binding.btnVideo.setOnClickListener { capture(AttachmentType.VIDEO) }
        binding.btnGallery.setOnClickListener {
            pickMedia.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                )
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (finishing) {
                    finish()
                } else {
                    // Kayıt sürerken yanlışlıkla çıkışı önle; kayıt arka planda sürer
                    moveTaskToBack(true)
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, RecordingService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        service?.listener = null
        runCatching { unbindService(connection) }
        service = null
        super.onStop()
    }

    // ---- RecordingService.Listener ----

    override fun onTick(elapsedMs: Long, level: Int) {
        runOnUiThread {
            binding.timerText.text = Fmt.duration(elapsedMs)
            binding.waveform.push(level)
        }
    }

    override fun onFinished(meetingId: String, durationMs: Long) {
        // Meta güncelleme ve işleme başlatma serviste yapılır; burada yalnızca
        // model eksikse (paketsiz derlemeler) kurulum önerilir ve detaya geçilir.
        if (finishing) return
        finishing = true
        runOnUiThread {
            lifecycleScope.launch {
                val meeting = withContext(Dispatchers.IO) { repo.loadMeeting(meetingId) }
                if (meeting == null) {
                    finish()
                    return@launch
                }
                ModelDownloadHelper.ensureModels(
                    this@RecordingActivity, lifecycleScope,
                    VoskModelManager.requiredLanguages(meeting.language)
                ) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            repo.loadMeeting(meetingId)?.let { m ->
                                // Model kayıt bittiğinde hazır değildiyse servis FAILED
                                // bırakmıştır; şimdi hazırsa işlemeyi başlat
                                if (m.status == MeetingStatus.FAILED) {
                                    m.status = MeetingStatus.PROCESSING
                                    m.errorMessage = ""
                                    repo.saveMeeting(m)
                                    ProcessingService.start(this@RecordingActivity, meetingId)
                                }
                            }
                        }
                        openDetail()
                    }
                }
            }
        }
    }

    // ---- Ekler ----

    private fun capture(type: AttachmentType) {
        val meeting = repo.loadMeeting(meetingId) ?: return
        val ext = if (type == AttachmentType.IMAGE) "jpg" else "mp4"
        val file = File(repo.attachmentsDir(meeting.id), "ek_${UUID.randomUUID().toString().take(8)}.$ext")
        pendingCaptureFile = file
        pendingCaptureType = type
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        if (type == AttachmentType.IMAGE) takePicture.launch(uri) else captureVideo.launch(uri)
    }

    private fun onCaptureResult(ok: Boolean) {
        val file = pendingCaptureFile ?: return
        pendingCaptureFile = null
        if (!ok || !file.exists() || file.length() == 0L) {
            file.delete()
            return
        }
        addAttachment(file, pendingCaptureType, if (pendingCaptureType == AttachmentType.IMAGE) "image/jpeg" else "video/mp4")
    }

    private fun copyPicked(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val type = if (mime.startsWith("video/")) AttachmentType.VIDEO else AttachmentType.IMAGE
            val ext = when {
                mime.contains("png") -> "png"
                mime.startsWith("video/") -> "mp4"
                else -> "jpg"
            }
            val file = File(repo.attachmentsDir(meetingId), "ek_${UUID.randomUUID().toString().take(8)}.$ext")
            contentResolver.openInputStream(uri)?.use { ins ->
                file.outputStream().use { ins.copyTo(it) }
            }
            if (file.exists() && file.length() > 0) {
                runOnUiThread { addAttachment(file, type, mime) }
            }
        }
    }

    private fun addAttachment(file: File, type: AttachmentType, mime: String) {
        val meeting = repo.loadMeeting(meetingId) ?: return
        meeting.attachments.add(
            Attachment(
                type = type,
                mimeType = mime,
                fileName = file.name,
                sizeBytes = file.length(),
                meetingOffsetMs = service?.currentElapsedMs ?: -1L
            )
        )
        repo.saveMeeting(meeting)
        updateAttachmentCount(meeting)
    }

    private fun updateAttachmentCount(meeting: Meeting) {
        binding.attachmentInfo.text =
            resources.getQuantityString(
                R.plurals.attachment_count, meeting.attachments.size, meeting.attachments.size
            )
    }

    // ---- Bitirme ----

    private fun confirmFinish() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.finish_recording_title)
            .setMessage(R.string.finish_recording_message)
            .setPositiveButton(R.string.finish_recording_confirm) { _, _ ->
                binding.btnFinish.isEnabled = false
                binding.btnPause.isEnabled = false
                binding.recordDot.clearAnimation()
                binding.statusText.text = getString(R.string.status_finishing)
                service?.finishRecording()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updatePauseButton() {
        val paused = service?.isPaused == true
        binding.btnPause.setText(if (paused) R.string.resume else R.string.pause)
        binding.btnPause.setIconResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        binding.statusText.text =
            getString(if (paused) R.string.status_paused else R.string.status_recording_live)
        // Kayıt sürerken yanıp sönen nokta, duraklatınca sabit kalır
        if (paused) {
            binding.recordDot.clearAnimation()
            binding.recordDot.alpha = 0.4f
        } else {
            binding.recordDot.alpha = 1f
            binding.recordDot.startAnimation(
                android.view.animation.AlphaAnimation(1f, 0.25f).apply {
                    duration = 750
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                }
            )
        }
    }

    private fun openDetail() {
        startActivity(
            Intent(this, MeetingDetailActivity::class.java)
                .putExtra(MeetingDetailActivity.EXTRA_MEETING_ID, meetingId)
        )
        finish()
    }

    companion object {
        const val EXTRA_MEETING_ID = "meeting_id"
    }
}
