package com.fatsar.toplanti.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fatsar.toplanti.R
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.databinding.ActivityNewMeetingBinding
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingMode
import com.fatsar.toplanti.model.MeetingStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Kayıt öncesi ekran (PRD 10.2): mikrofon izni, katılımcı rızası
 * hatırlatması (FR-002) ve isteğe bağlı geçici başlık.
 */
class NewMeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewMeetingBinding

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                startRecording()
            } else {
                // İzin reddi: gerekçe göster, kullanıcıyı zorlamadan alternatif sun (PRD 14.1)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.mic_denied_title)
                    .setMessage(R.string.mic_denied_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewMeetingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.consentCheck.setOnCheckedChangeListener { _, checked ->
            binding.btnStart.isEnabled = checked
        }
        binding.btnStart.isEnabled = false
        binding.btnStart.setOnClickListener { requestAndStart() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun requestAndStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.POST_NOTIFICATIONS)

        if (needed.isEmpty()) startRecording() else permissionRequest.launch(needed.toTypedArray())
    }

    private fun startRecording() {
        val repo = MeetingRepository(applicationContext)
        val meeting = Meeting(
            mode = MeetingMode.LIVE,
            status = MeetingStatus.RECORDING,
            startedAt = System.currentTimeMillis()
        )
        binding.titleInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            meeting.title = it
            meeting.titleConfirmed = true
        }
        repo.saveMeeting(meeting)
        startActivity(
            Intent(this, RecordingActivity::class.java)
                .putExtra(RecordingActivity.EXTRA_MEETING_ID, meeting.id)
        )
        finish()
    }
}
