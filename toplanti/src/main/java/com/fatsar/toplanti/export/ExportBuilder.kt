package com.fatsar.toplanti.export

import com.fatsar.toplanti.model.InsightType
import com.fatsar.toplanti.model.Meeting
import com.fatsar.toplanti.model.TaskStatus
import com.fatsar.toplanti.model.TranscriptSegment
import com.fatsar.toplanti.util.Fmt

/**
 * Paylaşım/dışa aktarma içeriklerini üretir. Bölüm sırası PRD 13.2'ye göre:
 * kısa özet, kararlar, görevler, önemli notlar, toplantı bilgileri.
 * Görevlerden yalnızca kullanıcı tarafından onaylanmış olanlar paylaşılır (FR-034).
 */
object ExportBuilder {

    data class Options(
        val includeSummary: Boolean = true,
        val includeDecisions: Boolean = true,
        val includeTasks: Boolean = true,
        val includeNotes: Boolean = true,
        val includeTranscript: Boolean = false,
        val cleanTranscript: Boolean = true
    )

    /** Bölüm başlığı (null = normal paragraf) ve metin çiftleri. */
    fun sections(
        meeting: Meeting,
        segments: List<TranscriptSegment>,
        speakerName: (String) -> String,
        opts: Options,
        untitled: String
    ): List<Pair<String?, String>> {
        val out = mutableListOf<Pair<String?, String>>()
        val title = meeting.displayTitle(untitled)
        out.add("h1" to title)
        out.add(null to "${Fmt.date(meeting.meetingDate)} • ${Fmt.duration(meeting.durationMs)}")

        if (opts.includeSummary && meeting.shortSummary.isNotBlank()) {
            out.add("h2" to "Kısa Özet")
            out.add(null to meeting.shortSummary)
            if (meeting.detailedSummary.isNotBlank()) {
                out.add("h2" to "Ayrıntılı Özet")
                out.add(null to meeting.detailedSummary)
            }
        }
        if (opts.includeDecisions) {
            val decisions = meeting.insights.filter { it.type == InsightType.DECISION }
            if (decisions.isNotEmpty()) {
                out.add("h2" to "Kararlar")
                decisions.forEach { out.add(null to "• ${it.content}") }
            }
        }
        if (opts.includeTasks) {
            val tasks = meeting.tasks.filter { it.status != TaskStatus.NEEDS_REVIEW }
            if (tasks.isNotEmpty()) {
                out.add("h2" to "Görevler")
                tasks.forEach { t ->
                    val owner = t.ownerText.ifBlank { "Sahip belirtilmedi" }
                    val due = t.dueTextOriginal.ifBlank { "termin belirtilmedi" }
                    out.add(null to "• ${t.title} — $owner, $due")
                }
            }
        }
        if (opts.includeNotes) {
            val notes = meeting.insights.filter { it.type == InsightType.IMPORTANT_NOTE }
            val questions = meeting.insights.filter { it.type == InsightType.QUESTION }
            val risks = meeting.insights.filter { it.type == InsightType.RISK }
            if (notes.isNotEmpty()) {
                out.add("h2" to "Önemli Notlar")
                notes.forEach { out.add(null to "• ${it.content}") }
            }
            if (questions.isNotEmpty()) {
                out.add("h2" to "Açık Sorular")
                questions.forEach { out.add(null to "• ${it.content}") }
            }
            if (risks.isNotEmpty()) {
                out.add("h2" to "Riskler")
                risks.forEach { out.add(null to "• ${it.content}") }
            }
        }

        out.add("h2" to "Toplantı Bilgileri")
        out.add(null to "Tarih: ${Fmt.dateTime(meeting.meetingDate)}")
        out.add(null to "Süre: ${Fmt.duration(meeting.durationMs)}")
        if (meeting.tags.isNotEmpty()) out.add(null to "Etiketler: ${meeting.tags.joinToString(", ")}")
        out.add(null to "Bu notlar cihaz üzerinde otomatik oluşturulmuştur; kesin kayıt değildir, kaynak kayıttan doğrulanabilir.")

        if (opts.includeTranscript && segments.isNotEmpty()) {
            out.add("h2" to if (opts.cleanTranscript) "Transkript (temiz)" else "Transkript (ham)")
            segments.forEach { s ->
                val text = s.displayText(opts.cleanTranscript)
                out.add(null to "${Fmt.timestamp(s.startMs)} ${speakerName(s.speakerId)}: $text")
            }
        }
        return out
    }

    fun plainText(sections: List<Pair<String?, String>>): String =
        buildString {
            for ((style, text) in sections) {
                when (style) {
                    "h1" -> {
                        appendLine(text)
                        appendLine("=".repeat(minOf(60, text.length)))
                    }
                    "h2" -> {
                        appendLine()
                        appendLine(text)
                        appendLine("-".repeat(minOf(60, text.length)))
                    }
                    else -> appendLine(text)
                }
            }
        }

    fun emailSubject(meeting: Meeting, untitled: String): String =
        "[Toplantı Notları] ${meeting.displayTitle(untitled)} — ${Fmt.date(meeting.meetingDate)}"
}
