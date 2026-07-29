package com.fatsar.toplanti.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.VoskModelManager
import com.fatsar.toplanti.audio.AudioProbe
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.databinding.ActivityMainBinding
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingMode
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.service.ProcessingService
import com.fatsar.toplanti.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ana ekran (PRD 10.1): iki eşdeğer giriş — canlı kayıt ve mevcut ses/video
 * dosyasından not çıkarma (FR-001, FR-006) — tarih gruplu arşiv, arama ve
 * durum göstergeleri.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: MeetingRepository
    private lateinit var adapter: MeetingListAdapter

    private val importFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) importAudio(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repo = MeetingRepository(applicationContext)
        adapter = MeetingListAdapter { meeting -> openMeeting(meeting) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnRecord.setOnClickListener {
            startActivity(Intent(this, NewMeetingActivity::class.java))
        }
        binding.btnImport.setOnClickListener { pickFiles() }
        binding.btnEmptyImport.setOnClickListener { pickFiles() }
        binding.searchInput.doAfterTextChanged { refresh() }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Başka uygulamalardan paylaşılan ses/video dosyalarını içe alır (FR-006). */
    private fun handleShareIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { importAudio(listOf(it)) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) importAudio(uris)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_import -> {
            pickFiles()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun pickFiles() {
        importFiles.launch(arrayOf("audio/*", "video/*"))
    }

    private fun refresh() {
        val query = binding.searchInput.text?.toString().orEmpty()
        lifecycleScope.launch {
            val meetings = withContext(Dispatchers.IO) { repo.search(query) }
            adapter.submit(meetings, getString(R.string.untitled_meeting))
            binding.emptyView.visibility = if (meetings.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openMeeting(meeting: Meeting) {
        startActivity(
            Intent(this, MeetingDetailActivity::class.java)
                .putExtra(MeetingDetailActivity.EXTRA_MEETING_ID, meeting.id)
        )
    }

    // ---- Dosyadan not çıkarma ----

    private data class ImportFailure(val name: String, val reason: String)

    /**
     * Seçilen dosyaları uygulama deposuna kopyalar, doğrular ve işleme
     * kuyruğuna alır. Ses izi olmayan/bozuk dosyalar işleme alınmadan
     * kullanıcıya bildirilir (PRD 15 hata tablosu).
     */
    private fun importAudio(uris: List<Uri>) {
        val progress = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_progress_title)
            .setMessage(resources.getQuantityString(R.plurals.import_progress, uris.size, uris.size))
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val imported = mutableListOf<Meeting>()
            val failures = mutableListOf<ImportFailure>()

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val meta = queryMeta(uri)
                    val displayName = meta.first ?: getString(R.string.imported_default_name)
                    val ext = displayName.substringAfterLast('.', "").lowercase().ifBlank { "bin" }
                    val meeting = Meeting(
                        mode = MeetingMode.IMPORTED,
                        status = MeetingStatus.PROCESSING,
                        language = VoskModelManager.LANG_AUTO,
                        audioFileName = "imported.$ext",
                        // Dosyanın kendi tarihi varsa arşivde doğru güne düşsün
                        meetingDate = meta.second ?: System.currentTimeMillis(),
                        // Kullanıcı onaylayana dek yalnızca öneri (AC-014)
                        suggestedTitle = displayName.substringBeforeLast('.').take(80)
                    )
                    val dest = File(repo.meetingDir(meeting.id), meeting.audioFileName)
                    val copied = runCatching {
                        contentResolver.openInputStream(uri)?.use { ins ->
                            dest.outputStream().use { ins.copyTo(it) }
                        } ?: throw IllegalStateException(getString(R.string.import_unreadable))
                    }
                    if (copied.isFailure || !dest.exists() || dest.length() == 0L) {
                        repo.deleteMeeting(meeting.id)
                        failures.add(
                            ImportFailure(
                                displayName,
                                copied.exceptionOrNull()?.message
                                    ?: getString(R.string.import_unreadable)
                            )
                        )
                        continue
                    }

                    val probe = AudioProbe.probe(dest)
                    if (!probe.hasAudio || probe.durationMs <= 0L) {
                        repo.deleteMeeting(meeting.id)
                        failures.add(ImportFailure(displayName, getString(R.string.import_no_audio)))
                        continue
                    }
                    meeting.durationMs = probe.durationMs
                    repo.saveMeeting(meeting)
                    imported.add(meeting)
                }
            }

            progress.dismiss()
            if (imported.isEmpty()) {
                showFailures(failures)
                return@launch
            }

            ModelDownloadHelper.ensureModels(
                this@MainActivity, lifecycleScope,
                VoskModelManager.requiredLanguages(VoskModelManager.LANG_AUTO)
            ) {
                imported.forEach { ProcessingService.start(this@MainActivity, it.id) }
                refresh()
                if (failures.isNotEmpty()) {
                    showFailures(failures)
                } else if (imported.size == 1) {
                    openMeeting(imported.first())
                }
            }
        }
    }

    private fun showFailures(failures: List<ImportFailure>) {
        if (failures.isEmpty()) return
        val detail = failures.joinToString("\n") { "• ${it.name}: ${it.reason}" }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_failed_title)
            .setMessage(getString(R.string.import_failed_body, detail))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** @return (görünen ad, son değiştirilme zamanı) */
    private fun queryMeta(uri: Uri): Pair<String?, Long?> {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, "last_modified")
        return runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null to null
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val dateIndex = c.getColumnIndex("last_modified")
                val name = if (nameIndex >= 0) c.getString(nameIndex) else null
                val date = if (dateIndex >= 0 && !c.isNull(dateIndex)) c.getLong(dateIndex) else null
                name to date?.takeIf { it > 0 }
            } ?: (null to null)
        }.getOrDefault(null to null)
    }
}
