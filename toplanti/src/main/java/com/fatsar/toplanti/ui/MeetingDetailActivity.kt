package com.fatsar.toplanti.ui

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fatsar.toplanti.R
import com.fatsar.toplanti.asr.VoskModelManager
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.databinding.ActivityMeetingDetailBinding
import com.fatsar.toplanti.export.ExportBuilder
import com.fatsar.toplanti.export.ExportFormat
import com.fatsar.toplanti.export.ShareManager
import com.fatsar.toplanti.model.Attachment
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.model.Speaker
import com.fatsar.toplanti.model.TaskItem
import com.fatsar.toplanti.model.TaskStatus
import com.fatsar.toplanti.model.TranscriptSegment
import com.fatsar.toplanti.service.ProcessingService
import com.fatsar.toplanti.util.Fmt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Toplantı detay ekranı (PRD 10.6): genel bakış, notlar, görevler,
 * transkript (ham/temiz), ekler; başlık onayı (10.5), paylaşım ve
 * dışa aktarma akışları.
 */
class MeetingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeetingDetailBinding
    private lateinit var repo: MeetingRepository
    private lateinit var shareManager: ShareManager
    private var meeting: Meeting? = null
    private var segments: MutableList<TranscriptSegment> = mutableListOf()
    private var showClean = true
    private var player: MediaPlayer? = null

    private lateinit var segmentAdapter: SegmentAdapter
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var insightAdapter: InsightAdapter
    private lateinit var attachmentAdapter: AttachmentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeetingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repo = MeetingRepository(applicationContext)
        shareManager = ShareManager(this, repo)

        segmentAdapter = SegmentAdapter(
            speakerName = { id -> speakerName(id) },
            showClean = { showClean },
            onClick = { seg -> playFrom(seg.startMs) },
            onLongClick = { seg -> editSegmentDialog(seg) }
        )
        taskAdapter = TaskAdapter { task -> taskDialog(task) }
        insightAdapter = InsightAdapter { insight ->
            insight.sourceSegmentIds.firstOrNull()?.let { segId ->
                segments.firstOrNull { it.id == segId }?.let { playFrom(it.startMs) }
            }
        }
        attachmentAdapter = AttachmentAdapter(
            onClick = { att -> openAttachment(att) },
            onLongClick = { att -> captionDialog(att) }
        )

        binding.notesRecycler.layoutManager = LinearLayoutManager(this)
        binding.notesRecycler.adapter = insightAdapter
        binding.tasksRecycler.layoutManager = LinearLayoutManager(this)
        binding.tasksRecycler.adapter = taskAdapter
        binding.transcriptRecycler.layoutManager = LinearLayoutManager(this)
        binding.transcriptRecycler.adapter = segmentAdapter
        binding.attachmentsRecycler.layoutManager = LinearLayoutManager(this)
        binding.attachmentsRecycler.adapter = attachmentAdapter

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.toggleTranscript.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                showClean = checkedId == R.id.btnClean
                segmentAdapter.notifyDataSetChanged()
            }
        }

        binding.btnConfirmTitle.setOnClickListener { confirmTitle() }
        binding.btnCompleteReview.setOnClickListener { completeReview() }
        binding.btnRetry.setOnClickListener { reprocess() }
        binding.btnAddTask.setOnClickListener { taskDialog(null) }
        binding.btnStopPlayback.setOnClickListener { stopPlayback() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onStop() {
        stopPlayback()
        super.onStop()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share_email -> {
            shareFlow(email = true); true
        }
        R.id.action_export -> {
            exportFlow(); true
        }
        R.id.action_tags -> {
            tagsDialog(); true
        }
        R.id.action_reprocess -> {
            reprocess(); true
        }
        R.id.action_delete -> {
            deleteFlow(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---- Veri yükleme ve görünüm ----

    private fun reload() {
        val id = intent.getStringExtra(EXTRA_MEETING_ID) ?: return finish()
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repo.loadMeeting(id)?.let { m -> m to repo.loadSegments(id).toMutableList() }
            }
            if (loaded == null) {
                finish()
                return@launch
            }
            meeting = loaded.first
            segments = loaded.second
            render()
        }
    }

    private fun render() {
        val m = meeting ?: return
        binding.titleText.text = m.displayTitle(getString(R.string.untitled_meeting))
        val statusLabel = when (m.status) {
            MeetingStatus.RECORDING -> getString(R.string.status_recording)
            MeetingStatus.PROCESSING -> getString(R.string.status_processing)
            MeetingStatus.REVIEW -> getString(R.string.status_review)
            MeetingStatus.DONE -> getString(R.string.status_done)
            MeetingStatus.FAILED -> getString(R.string.status_failed)
        }
        val langLabel = when (m.language) {
            VoskModelManager.LANG_TR -> " • " + getString(R.string.lang_tr)
            VoskModelManager.LANG_EN -> " • " + getString(R.string.lang_en)
            else -> ""
        }
        binding.metaText.text =
            "${Fmt.dateTime(m.meetingDate)} • ${Fmt.duration(m.durationMs)} • $statusLabel$langLabel" +
                if (m.tags.isNotEmpty()) "\n" + m.tags.joinToString(", ") { "#$it" } else ""

        // Başlık onayı kartı (AC-014): öneri, kullanıcı onaylayana dek nihai olmaz
        val needsTitle = m.status == MeetingStatus.REVIEW && !m.titleConfirmed
        binding.titleReviewCard.visibility = if (needsTitle) View.VISIBLE else View.GONE
        if (needsTitle) {
            binding.suggestedTitleInput.setText(
                m.suggestedTitle.ifBlank { m.displayTitle(getString(R.string.untitled_meeting)) }
            )
        }
        binding.btnCompleteReview.visibility =
            if (m.status == MeetingStatus.REVIEW) View.VISIBLE else View.GONE

        binding.errorCard.visibility = if (m.status == MeetingStatus.FAILED) View.VISIBLE else View.GONE
        if (m.status == MeetingStatus.FAILED) {
            binding.errorText.text = getString(R.string.processing_failed_detail, m.errorMessage)
        }
        binding.processingBanner.visibility =
            if (m.status == MeetingStatus.PROCESSING) View.VISIBLE else View.GONE

        // Genel bakış
        binding.shortSummaryText.text = m.shortSummary.ifBlank { getString(R.string.no_summary) }
        binding.detailedSummaryText.text = m.detailedSummary
        binding.detailedSummaryHeader.visibility =
            if (m.detailedSummary.isBlank()) View.GONE else View.VISIBLE
        binding.confidenceNote.text = getString(
            R.string.confidence_note,
            (m.summaryConfidence * 100).toInt()
        )

        insightAdapter.submit(m.insights, this)
        taskAdapter.submit(m.tasks, this)
        segmentAdapter.submit(segments)
        attachmentAdapter.submit(m.attachments)

        showTab(binding.tabs.selectedTabPosition.coerceAtLeast(0))
    }

    private fun showTab(position: Int) {
        binding.overviewScroll.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.notesRecycler.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.tasksContainer.visibility = if (position == 2) View.VISIBLE else View.GONE
        binding.transcriptContainer.visibility = if (position == 3) View.VISIBLE else View.GONE
        binding.attachmentsRecycler.visibility = if (position == 4) View.VISIBLE else View.GONE
    }

    private fun speakerName(id: String): String =
        meeting?.speakers?.firstOrNull { it.id == id }?.shownName()
            ?: getString(R.string.speaker_default, 1)

    private fun save() {
        val m = meeting ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            repo.saveMeeting(m)
            repo.saveSegments(m.id, segments)
        }
    }

    // ---- Başlık ve inceleme ----

    private fun confirmTitle() {
        val m = meeting ?: return
        val text = binding.suggestedTitleInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        m.title = text
        m.titleConfirmed = true
        save()
        render()
    }

    private fun completeReview() {
        val m = meeting ?: return
        if (!m.titleConfirmed) {
            confirmTitle()
            if (!m.titleConfirmed) return
        }
        m.status = MeetingStatus.DONE
        save()
        render()
    }

    private fun reprocess() {
        val m = meeting ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reprocess_title)
            .setMessage(R.string.reprocess_message)
            .setPositiveButton(R.string.reprocess_confirm) { _, _ ->
                ModelDownloadHelper.ensureModels(
                    this, lifecycleScope, VoskModelManager.requiredLanguages(m.language)
                ) {
                    m.status = MeetingStatus.PROCESSING
                    lifecycleScope.launch(Dispatchers.IO) {
                        repo.saveMeeting(m)
                        withContext(Dispatchers.Main) {
                            ProcessingService.start(this@MeetingDetailActivity, m.id)
                            render()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Transkript düzenleme (FR-017) ----

    private fun editSegmentDialog(seg: TranscriptSegment) {
        val edit = makeEdit(seg.displayText(showClean))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.edit_segment_title, Fmt.timestamp(seg.startMs)))
            .setView(wrap(edit))
            .setPositiveButton(R.string.save) { _, _ ->
                val newText = edit.text.toString().trim()
                if (newText.isNotBlank() && newText != seg.rawText) {
                    seg.userText = newText
                    seg.revision++
                }
                save()
                segmentAdapter.notifyDataSetChanged()
            }
            .setNeutralButton(R.string.change_speaker) { _, _ -> speakerDialog(seg) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun speakerDialog(seg: TranscriptSegment) {
        val m = meeting ?: return
        val names = m.speakers.map { it.shownName() } +
            getString(R.string.add_speaker) + getString(R.string.rename_speaker)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_speaker)
            .setItems(names.toTypedArray()) { _, which ->
                when {
                    which < m.speakers.size -> {
                        seg.speakerId = m.speakers[which].id
                        save()
                        segmentAdapter.notifyDataSetChanged()
                    }
                    which == m.speakers.size -> addSpeakerDialog(seg)
                    else -> renameSpeakerDialog()
                }
            }
            .show()
    }

    private fun addSpeakerDialog(seg: TranscriptSegment?) {
        val m = meeting ?: return
        val edit = makeEdit("")
        edit.hint = getString(R.string.speaker_name_hint)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_speaker)
            .setView(wrap(edit))
            .setPositiveButton(R.string.save) { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotBlank()) {
                    val sp = Speaker(
                        label = getString(R.string.speaker_default, m.speakers.size + 1),
                        displayName = name,
                        confirmedByUser = true
                    )
                    m.speakers.add(sp)
                    seg?.speakerId = sp.id
                    save()
                    segmentAdapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Konuşmacı adını değiştirir; o konuşmacıya bağlı tüm segmentler güncellenir (AC-012). */
    private fun renameSpeakerDialog() {
        val m = meeting ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_speaker)
            .setItems(m.speakers.map { it.shownName() }.toTypedArray()) { _, which ->
                val sp = m.speakers[which]
                val edit = makeEdit(sp.shownName())
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.rename_speaker)
                    .setView(wrap(edit))
                    .setPositiveButton(R.string.save) { _, _ ->
                        val name = edit.text.toString().trim()
                        if (name.isNotBlank()) {
                            sp.displayName = name
                            sp.confirmedByUser = true
                            save()
                            segmentAdapter.notifyDataSetChanged()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .show()
    }

    // ---- Görevler (FR-033) ----

    private fun taskDialog(existing: TaskItem?) {
        val m = meeting ?: return
        val titleEdit = makeEdit(existing?.title ?: "").apply { hint = getString(R.string.task_title_hint) }
        val ownerEdit = makeEdit(existing?.ownerText ?: "").apply { hint = getString(R.string.task_owner_hint) }
        val dueEdit = makeEdit(existing?.dueTextOriginal ?: "").apply { hint = getString(R.string.task_due_hint) }
        val confirmed = CheckBox(this).apply {
            text = getString(R.string.task_confirmed_label)
            isChecked = existing != null && existing.status != TaskStatus.NEEDS_REVIEW
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(titleEdit)
            addView(ownerEdit)
            addView(dueEdit)
            addView(confirmed)
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_task else R.string.edit_task)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = titleEdit.text.toString().trim()
                if (title.isBlank()) return@setPositiveButton
                if (existing == null) {
                    m.tasks.add(
                        TaskItem(
                            title = title,
                            ownerText = ownerEdit.text.toString().trim(),
                            dueTextOriginal = dueEdit.text.toString().trim(),
                            status = if (confirmed.isChecked) TaskStatus.CONFIRMED else TaskStatus.NEEDS_REVIEW,
                            confidence = 1.0
                        )
                    )
                } else {
                    existing.title = title
                    existing.ownerText = ownerEdit.text.toString().trim()
                    existing.dueTextOriginal = dueEdit.text.toString().trim()
                    existing.status =
                        if (confirmed.isChecked) TaskStatus.CONFIRMED else TaskStatus.NEEDS_REVIEW
                }
                save()
                taskAdapter.submit(m.tasks, this)
            }
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                m.tasks.remove(existing)
                save()
                taskAdapter.submit(m.tasks, this)
            }
        }
        builder.show()
    }

    // ---- Etiketler (FR-053) ----

    private fun tagsDialog() {
        val m = meeting ?: return
        val edit = makeEdit(m.tags.joinToString(", "))
        edit.hint = getString(R.string.tags_hint)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_tags)
            .setView(wrap(edit))
            .setPositiveButton(R.string.save) { _, _ ->
                m.tags.clear()
                m.tags.addAll(
                    edit.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }
                )
                save()
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Paylaşım ve dışa aktarma (FR-060..FR-064) ----

    private fun sectionDialog(onSelected: (ExportBuilder.Options) -> Unit) {
        val labels = arrayOf(
            getString(R.string.section_summary),
            getString(R.string.section_decisions),
            getString(R.string.section_tasks),
            getString(R.string.section_notes),
            getString(R.string.section_transcript)
        )
        // Transkript varsayılan olarak paylaşılmaz (PRD 13.2)
        val checked = booleanArrayOf(true, true, true, true, false)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_sections_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.next) { _, _ ->
                onSelected(
                    ExportBuilder.Options(
                        includeSummary = checked[0],
                        includeDecisions = checked[1],
                        includeTasks = checked[2],
                        includeNotes = checked[3],
                        includeTranscript = checked[4],
                        cleanTranscript = showClean
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun attachmentDialog(onSelected: (List<Attachment>) -> Unit) {
        val m = meeting ?: return
        if (m.attachments.isEmpty()) {
            onSelected(emptyList())
            return
        }
        val labels = m.attachments.map {
            "${it.fileName} (${Fmt.fileSize(it.sizeBytes)})"
        }.toTypedArray()
        // Ekler varsayılan olarak seçili değildir; kullanıcı bilinçli ekler (PRD 13.2)
        val checked = BooleanArray(m.attachments.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_attachments_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.next) { _, _ ->
                onSelected(m.attachments.filterIndexed { i, _ -> checked[i] })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareFlow(email: Boolean) {
        val m = meeting ?: return
        sectionDialog { opts ->
            attachmentDialog { atts ->
                if (email) {
                    val intent = shareManager.buildEmailIntent(m, segments, opts, atts)
                    startActivity(Intent.createChooser(intent, getString(R.string.share_via_email)))
                }
            }
        }
    }

    private fun exportFlow() {
        val m = meeting ?: return
        val formats = ExportFormat.values()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_format_title)
            .setItems(formats.map { it.name }.toTypedArray()) { _, which ->
                val format = formats[which]
                sectionDialog { opts ->
                    if (format == ExportFormat.ZIP) {
                        attachmentDialog { atts -> doExport(m, format, opts, atts) }
                    } else {
                        doExport(m, format, opts, emptyList())
                    }
                }
            }
            .show()
    }

    private fun doExport(
        m: Meeting,
        format: ExportFormat,
        opts: ExportBuilder.Options,
        atts: List<Attachment>
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { shareManager.export(m, segments, format, opts, atts) }
            }
            result.fold(
                onSuccess = { (_, intent) ->
                    startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))
                },
                onFailure = { e ->
                    MaterialAlertDialogBuilder(this@MeetingDetailActivity)
                        .setTitle(R.string.export_failed)
                        .setMessage(e.message ?: e.javaClass.simpleName)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            )
        }
    }

    // ---- Silme (FR-054, AC-034) ----

    private fun deleteFlow() {
        val m = meeting ?: return
        val summary = getString(
            R.string.delete_summary,
            segments.size, m.insights.size, m.tasks.size, m.attachments.size
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_meeting_title)
            .setMessage(summary)
            .setPositiveButton(R.string.delete_confirm) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.deleteMeeting(m.id)
                    withContext(Dispatchers.Main) { finish() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Ekler ----

    private fun openAttachment(att: Attachment) {
        val m = meeting ?: return
        val file = repo.attachmentFile(m, att)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, att.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun captionDialog(att: Attachment) {
        val edit = makeEdit(att.caption)
        edit.hint = getString(R.string.caption_hint)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_caption)
            .setView(wrap(edit))
            .setPositiveButton(R.string.save) { _, _ ->
                att.caption = edit.text.toString().trim()
                save()
                attachmentAdapter.submit(meeting?.attachments ?: mutableListOf())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Kaynak zaman damgasından dinleme (AC-011, FR-016) ----

    private fun playFrom(ms: Long) {
        val m = meeting ?: return
        val audio = repo.audioFile(m) ?: return
        if (!audio.exists()) return
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(audio.absolutePath)
            prepare()
            seekTo(ms.toInt())
            start()
        }
        binding.playbackBar.visibility = View.VISIBLE
        binding.playbackText.text = getString(R.string.playing_from, Fmt.duration(ms))
    }

    private fun stopPlayback() {
        player?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        player = null
        binding.playbackBar.visibility = View.GONE
    }

    // ---- Küçük yardımcılar ----

    private fun makeEdit(initial: String): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setText(initial)
    }

    private fun wrap(view: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    companion object {
        const val EXTRA_MEETING_ID = "meeting_id"
    }
}
