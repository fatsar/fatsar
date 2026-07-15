package com.fatsar.toplanti.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.fatsar.toplanti.R
import com.fatsar.toplanti.data.MeetingRepository
import com.fatsar.toplanti.model.Attachment
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.TranscriptSegment
import com.fatsar.toplanti.util.Fmt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val extension: String, val mime: String) {
    TXT("txt", "text/plain"),
    JSON("json", "application/json"),
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    ZIP("zip", "application/zip")
}

/**
 * Dışa aktarma dosyalarını üretir ve paylaşım/e-posta intentleri hazırlar.
 * E-posta, kullanıcının kendi e-posta uygulamasında taslak olarak açılır;
 * uygulama kendiliğinden hiçbir şey göndermez (FR-062).
 */
class ShareManager(private val context: Context, private val repo: MeetingRepository) {

    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

    fun buildEmailIntent(
        meeting: Meeting,
        segments: List<TranscriptSegment>,
        opts: ExportBuilder.Options,
        attachments: List<Attachment>
    ): Intent {
        val untitled = context.getString(R.string.untitled_meeting)
        val sections = ExportBuilder.sections(meeting, segments, speakerNamer(meeting), opts, untitled)
        val body = ExportBuilder.plainText(sections)
        val uris = ArrayList<Uri>()
        attachments.forEach { att ->
            val f = repo.attachmentFile(meeting, att)
            if (f.exists()) uris.add(toUri(f))
        }
        val intent = if (uris.isEmpty()) Intent(Intent.ACTION_SEND).apply { type = "message/rfc822" }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, ExportBuilder.emailSubject(meeting, untitled))
        intent.putExtra(Intent.EXTRA_TEXT, body)
        return intent
    }

    /** Seçilen biçimde dosya üretir ve paylaşılabilir URI ile birlikte döndürür. */
    fun export(
        meeting: Meeting,
        segments: List<TranscriptSegment>,
        format: ExportFormat,
        opts: ExportBuilder.Options,
        attachments: List<Attachment> = emptyList()
    ): Pair<File, Intent> {
        val untitled = context.getString(R.string.untitled_meeting)
        val sections = ExportBuilder.sections(meeting, segments, speakerNamer(meeting), opts, untitled)
        val base = Fmt.safeFileName(meeting.displayTitle(untitled))
        val file = File(exportDir, "$base.${format.extension}")

        when (format) {
            ExportFormat.TXT -> file.writeText(ExportBuilder.plainText(sections))
            ExportFormat.JSON -> file.writeText(buildJson(meeting, segments).toString(2))
            ExportFormat.PDF -> file.outputStream().use { PdfExporter.write(it, sections) }
            ExportFormat.DOCX -> file.outputStream().use { DocxWriter.write(it, sections) }
            ExportFormat.ZIP -> {
                // ZIP: PDF + TXT + JSON + seçilen ekler (FR-064)
                ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("$base.txt"))
                    zip.write(ExportBuilder.plainText(sections).toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("$base.json"))
                    zip.write(buildJson(meeting, segments).toString(2).toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("$base.pdf"))
                    PdfExporter.write(NonClosingStream(zip), sections)
                    zip.closeEntry()
                    attachments.forEach { att ->
                        val f = repo.attachmentFile(meeting, att)
                        if (f.exists()) {
                            zip.putNextEntry(ZipEntry("ekler/${att.fileName}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, toUri(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return file to intent
    }

    private fun speakerNamer(meeting: Meeting): (String) -> String = { id ->
        meeting.speakers.firstOrNull { it.id == id }?.shownName()
            ?: context.getString(R.string.speaker_default, 1)
    }

    private fun toUri(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    private fun buildJson(meeting: Meeting, segments: List<TranscriptSegment>): JSONObject =
        JSONObject()
            .put("title", meeting.displayTitle(""))
            .put("meetingDate", meeting.meetingDate)
            .put("durationMs", meeting.durationMs)
            .put("language", meeting.language)
            .put("tags", JSONArray(meeting.tags))
            .put("shortSummary", meeting.shortSummary)
            .put("detailedSummary", meeting.detailedSummary)
            .put("insights", JSONArray().apply {
                meeting.insights.forEach {
                    put(
                        JSONObject()
                            .put("type", it.type.name)
                            .put("content", it.content)
                            .put("confidence", it.confidence)
                            .put("sourceSegmentIds", JSONArray(it.sourceSegmentIds))
                    )
                }
            })
            .put("tasks", JSONArray().apply {
                meeting.tasks.forEach {
                    put(
                        JSONObject()
                            .put("title", it.title)
                            .put("owner", it.ownerText)
                            .put("due", it.dueTextOriginal)
                            .put("dueAtMillis", it.dueAtMillis)
                            .put("status", it.status.name)
                            .put("confidence", it.confidence)
                    )
                }
            })
            .put("segments", JSONArray().apply {
                segments.forEach { s ->
                    put(
                        JSONObject()
                            .put("id", s.id)
                            .put("startMs", s.startMs)
                            .put("endMs", s.endMs)
                            .put("speaker", speakerNamer(meeting)(s.speakerId))
                            .put("rawText", s.rawText)
                            .put("cleanText", s.cleanText)
                            .put("userText", s.userText ?: JSONObject.NULL)
                            .put("confidence", s.confidence)
                    )
                }
            })

    /** ZipOutputStream'i kapatmadan PdfDocument.writeTo kullanmak için sarmalayıcı. */
    private class NonClosingStream(private val delegate: java.io.OutputStream) : java.io.OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() { /* bilinçli olarak kapatılmaz */ }
    }
}
