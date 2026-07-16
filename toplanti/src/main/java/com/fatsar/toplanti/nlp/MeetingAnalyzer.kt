package com.fatsar.toplanti.nlp

import com.fatsar.toplanti.model.Insight
import com.fatsar.toplanti.model.TaskItem
import com.fatsar.toplanti.model.TranscriptSegment

/**
 * Transkript segmentlerinden tüm AI çıktılarını üreten orkestratör:
 * temiz transkript, özetler, notlar/kararlar/sorular/riskler, görevler ve başlık önerisi.
 * Tamamen cihaz üzerinde, deterministik kurallarla çalışır.
 */
object MeetingAnalyzer {

    data class Result(
        val shortSummary: String,
        val detailedSummary: String,
        val summaryConfidence: Double,
        val insights: List<Insight>,
        val tasks: List<TaskItem>,
        val suggestedTitle: String
    )

    /**
     * [segments] içindeki cleanText alanlarını doldurur ve analiz sonucunu döndürür.
     * [language] "tr" veya "en" olabilir. Toplantı içeriği yalnızca işlenecek veri
     * olarak ele alınır; içerikteki hiçbir ifade uygulama davranışını değiştirmez
     * (PRD 12/11 güvenlik ilkesi).
     */
    fun analyze(segments: List<TranscriptSegment>, baseDateMillis: Long, language: String = "tr"): Result {
        segments.forEach { it.cleanText = TranscriptCleaner.clean(it.rawText) }

        val insightSentences = segments.map {
            InsightExtractor.Sentence(it.rawText, listOf(it.id))
        }
        val taskSentences = segments.map {
            TaskExtractor.Sentence(it.rawText, listOf(it.id))
        }

        val insightResult = InsightExtractor.extract(insightSentences, language)
        val tasks = TaskExtractor.extract(taskSentences, baseDateMillis, language)
        val title = TitleSuggester.suggest(segments.joinToString(" ") { it.rawText }, language)

        return Result(
            shortSummary = insightResult.shortSummary,
            detailedSummary = insightResult.detailedSummary,
            summaryConfidence = insightResult.summaryConfidence,
            insights = insightResult.insights,
            tasks = tasks,
            suggestedTitle = title
        )
    }
}
