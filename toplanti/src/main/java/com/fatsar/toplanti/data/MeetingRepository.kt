package com.fatsar.toplanti.data

import android.content.Context
import com.fatsar.toplanti.model.Attachment
import com.fatsar.toplanti.model.AttachmentType
import com.fatsar.toplanti.model.Insight
import com.fatsar.toplanti.model.InsightType
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.MeetingMode
import com.fatsar.toplanti.model.MeetingStatus
import com.fatsar.toplanti.model.Speaker
import com.fatsar.toplanti.model.TaskItem
import com.fatsar.toplanti.model.TaskStatus
import com.fatsar.toplanti.model.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cihaz üzerinde, uygulamaya özel (sandbox) depolamada JSON tabanlı toplantı deposu.
 * Yapı: filesDir/meetings/<id>/meeting.json + segments.json + ses dosyası + attachments/
 */
class MeetingRepository(context: Context) {

    private val root: File = File(context.filesDir, "meetings").apply { mkdirs() }

    fun meetingDir(id: String): File = File(root, id).apply { mkdirs() }

    fun attachmentsDir(id: String): File = File(meetingDir(id), "attachments").apply { mkdirs() }

    fun audioFile(meeting: Meeting): File? =
        meeting.audioFileName.takeIf { it.isNotBlank() }?.let { File(meetingDir(meeting.id), it) }

    fun attachmentFile(meeting: Meeting, attachment: Attachment): File =
        File(attachmentsDir(meeting.id), attachment.fileName)

    // ---- Toplantı meta ----

    fun saveMeeting(meeting: Meeting) {
        meeting.updatedAt = System.currentTimeMillis()
        File(meetingDir(meeting.id), META_FILE).writeText(toJson(meeting).toString())
    }

    fun loadMeeting(id: String): Meeting? {
        val f = File(meetingDir(id), META_FILE)
        if (!f.exists()) return null
        return runCatching { fromJson(JSONObject(f.readText())) }.getOrNull()
    }

    fun loadAllMeetings(): List<Meeting> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { loadMeeting(it.name) }
            .sortedByDescending { it.meetingDate }

    /** Toplantıyı ve ona bağlı TÜM verileri kalıcı siler (FR-054, AC-034). */
    fun deleteMeeting(id: String) {
        File(root, id).deleteRecursively()
    }

    // ---- Segmentler ----

    fun saveSegments(meetingId: String, segments: List<TranscriptSegment>) {
        val arr = JSONArray()
        segments.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("speakerId", s.speakerId)
                    .put("startMs", s.startMs)
                    .put("endMs", s.endMs)
                    .put("rawText", s.rawText)
                    .put("cleanText", s.cleanText)
                    .put("userText", s.userText ?: JSONObject.NULL)
                    .put("confidence", s.confidence)
                    .put("revision", s.revision)
            )
        }
        File(meetingDir(meetingId), SEGMENTS_FILE).writeText(arr.toString())
    }

    fun loadSegments(meetingId: String): List<TranscriptSegment> {
        val f = File(meetingDir(meetingId), SEGMENTS_FILE)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TranscriptSegment(
                    id = o.getString("id"),
                    speakerId = o.optString("speakerId"),
                    startMs = o.getLong("startMs"),
                    endMs = o.getLong("endMs"),
                    rawText = o.getString("rawText"),
                    cleanText = o.optString("cleanText"),
                    userText = if (o.isNull("userText")) null else o.getString("userText"),
                    confidence = o.optDouble("confidence", 1.0),
                    revision = o.optInt("revision")
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Başlık, etiket ve transkript metni üzerinden arama (FR-052). */
    fun search(query: String): List<Meeting> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return loadAllMeetings()
        return loadAllMeetings().filter { m ->
            m.displayTitle("").lowercase().contains(q) ||
                m.tags.any { it.lowercase().contains(q) } ||
                loadSegments(m.id).any { it.displayText(false).lowercase().contains(q) }
        }
    }

    // ---- JSON dönüşümleri ----

    private fun toJson(m: Meeting): JSONObject = JSONObject()
        .put("id", m.id)
        .put("title", m.title)
        .put("suggestedTitle", m.suggestedTitle)
        .put("titleConfirmed", m.titleConfirmed)
        .put("meetingDate", m.meetingDate)
        .put("startedAt", m.startedAt)
        .put("endedAt", m.endedAt)
        .put("durationMs", m.durationMs)
        .put("mode", m.mode.name)
        .put("language", m.language)
        .put("status", m.status.name)
        .put("audioFileName", m.audioFileName)
        .put("errorMessage", m.errorMessage)
        .put("tags", JSONArray(m.tags))
        .put("shortSummary", m.shortSummary)
        .put("detailedSummary", m.detailedSummary)
        .put("summaryConfidence", m.summaryConfidence)
        .put("createdAt", m.createdAt)
        .put("updatedAt", m.updatedAt)
        .put("speakers", JSONArray().apply {
            m.speakers.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("label", it.label)
                        .put("displayName", it.displayName)
                        .put("confirmedByUser", it.confirmedByUser)
                )
            }
        })
        .put("insights", JSONArray().apply {
            m.insights.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("type", it.type.name)
                        .put("content", it.content)
                        .put("confidence", it.confidence)
                        .put("sourceSegmentIds", JSONArray(it.sourceSegmentIds))
                )
            }
        })
        .put("tasks", JSONArray().apply {
            m.tasks.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("description", it.description)
                        .put("ownerText", it.ownerText)
                        .put("dueTextOriginal", it.dueTextOriginal)
                        .put("dueAtMillis", it.dueAtMillis)
                        .put("status", it.status.name)
                        .put("confidence", it.confidence)
                        .put("sourceSegmentIds", JSONArray(it.sourceSegmentIds))
                )
            }
        })
        .put("attachments", JSONArray().apply {
            m.attachments.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("type", it.type.name)
                        .put("mimeType", it.mimeType)
                        .put("fileName", it.fileName)
                        .put("sizeBytes", it.sizeBytes)
                        .put("capturedAt", it.capturedAt)
                        .put("meetingOffsetMs", it.meetingOffsetMs)
                        .put("caption", it.caption)
                )
            }
        })

    private fun fromJson(o: JSONObject): Meeting {
        val m = Meeting(
            id = o.getString("id"),
            title = o.optString("title"),
            suggestedTitle = o.optString("suggestedTitle"),
            titleConfirmed = o.optBoolean("titleConfirmed"),
            meetingDate = o.optLong("meetingDate"),
            startedAt = o.optLong("startedAt"),
            endedAt = o.optLong("endedAt"),
            durationMs = o.optLong("durationMs"),
            mode = MeetingMode.valueOf(o.optString("mode", MeetingMode.LIVE.name)),
            language = o.optString("language", "tr"),
            status = MeetingStatus.valueOf(o.optString("status", MeetingStatus.FAILED.name)),
            audioFileName = o.optString("audioFileName"),
            errorMessage = o.optString("errorMessage"),
            shortSummary = o.optString("shortSummary"),
            detailedSummary = o.optString("detailedSummary"),
            summaryConfidence = o.optDouble("summaryConfidence", 0.0),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt")
        )
        o.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).forEach { m.tags.add(arr.getString(it)) }
        }
        o.optJSONArray("speakers")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val s = arr.getJSONObject(i)
                m.speakers.add(
                    Speaker(
                        id = s.getString("id"),
                        label = s.getString("label"),
                        displayName = s.optString("displayName"),
                        confirmedByUser = s.optBoolean("confirmedByUser")
                    )
                )
            }
        }
        o.optJSONArray("insights")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val s = arr.getJSONObject(i)
                m.insights.add(
                    Insight(
                        id = s.getString("id"),
                        type = InsightType.valueOf(s.getString("type")),
                        content = s.getString("content"),
                        confidence = s.optDouble("confidence", 0.0),
                        sourceSegmentIds = s.optJSONArray("sourceSegmentIds").toStringList()
                    )
                )
            }
        }
        o.optJSONArray("tasks")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val s = arr.getJSONObject(i)
                m.tasks.add(
                    TaskItem(
                        id = s.getString("id"),
                        title = s.getString("title"),
                        description = s.optString("description"),
                        ownerText = s.optString("ownerText"),
                        dueTextOriginal = s.optString("dueTextOriginal"),
                        dueAtMillis = s.optLong("dueAtMillis"),
                        status = TaskStatus.valueOf(s.optString("status", TaskStatus.NEEDS_REVIEW.name)),
                        confidence = s.optDouble("confidence", 0.0),
                        sourceSegmentIds = s.optJSONArray("sourceSegmentIds").toStringList()
                    )
                )
            }
        }
        o.optJSONArray("attachments")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val s = arr.getJSONObject(i)
                m.attachments.add(
                    Attachment(
                        id = s.getString("id"),
                        type = AttachmentType.valueOf(s.getString("type")),
                        mimeType = s.getString("mimeType"),
                        fileName = s.getString("fileName"),
                        sizeBytes = s.optLong("sizeBytes"),
                        capturedAt = s.optLong("capturedAt"),
                        meetingOffsetMs = s.optLong("meetingOffsetMs", -1L),
                        caption = s.optString("caption")
                    )
                )
            }
        }
        return m
    }

    private fun JSONArray?.toStringList(): List<String> {
        this ?: return emptyList()
        return (0 until length()).map { getString(it) }
    }

    companion object {
        private const val META_FILE = "meeting.json"
        private const val SEGMENTS_FILE = "segments.json"
    }
}
