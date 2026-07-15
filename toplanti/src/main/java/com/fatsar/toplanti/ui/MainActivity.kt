package com.fatsar.toplanti.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fatsar.toplanti.R
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.databinding.ActivityMainBinding
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingMode
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.service.ProcessingService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ana ekran (PRD 10.1): yeni toplantı, dosya içe aktarma, tarih gruplu
 * arşiv listesi, arama ve durum göstergeleri.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: MeetingRepository
    private lateinit var adapter: MeetingListAdapter

    private val importFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importAudio(it) }
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

        binding.fabNew.setOnClickListener {
            startActivity(Intent(this, NewMeetingActivity::class.java))
        }
        binding.searchInput.doAfterTextChanged { refresh() }

        // Başka uygulamadan paylaşılan ses/video dosyası (içe aktarma, FR-006)
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { importAudio(it) }
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
            importFile.launch(arrayOf("audio/*", "video/*"))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun refresh() {
        val query = binding.searchInput.text?.toString().orEmpty()
        lifecycleScope.launch {
            val meetings = withContext(Dispatchers.IO) { repo.search(query) }
            adapter.submit(meetings, getString(R.string.untitled_meeting))
            binding.emptyView.visibility =
                if (meetings.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun openMeeting(meeting: Meeting) {
        startActivity(
            Intent(this, MeetingDetailActivity::class.java)
                .putExtra(MeetingDetailActivity.EXTRA_MEETING_ID, meeting.id)
        )
    }

    /** Mevcut ses/video dosyasını yeni toplantı olarak içe aktarır (FR-006, FR-007). */
    private fun importAudio(uri: Uri) {
        lifecycleScope.launch {
            val meeting = withContext(Dispatchers.IO) {
                val name = queryDisplayName(uri) ?: "kayit"
                val ext = name.substringAfterLast('.', "").lowercase().ifBlank { "bin" }
                val meeting = Meeting(
                    mode = MeetingMode.IMPORTED,
                    status = MeetingStatus.PROCESSING,
                    audioFileName = "imported.$ext"
                )
                val dest = File(repo.meetingDir(meeting.id), meeting.audioFileName)
                contentResolver.openInputStream(uri)?.use { ins ->
                    dest.outputStream().use { ins.copyTo(it) }
                } ?: run {
                    repo.deleteMeeting(meeting.id)
                    return@withContext null
                }
                repo.saveMeeting(meeting)
                meeting
            }
            if (meeting == null) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setMessage(R.string.import_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            ModelDownloadHelper.ensureModel(this@MainActivity, lifecycleScope) {
                ProcessingService.start(this@MainActivity, meeting.id)
                refresh()
                openMeeting(meeting)
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
