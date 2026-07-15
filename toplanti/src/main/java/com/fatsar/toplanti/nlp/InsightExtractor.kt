package com.fatsar.toplanti.nlp

import com.fatsar.toplanti.model.Insight
import com.fatsar.toplanti.model.InsightType

/**
 * Cümlelerden özet, önemli not, karar, açık soru ve risk maddeleri çıkarır
 * (FR-020, FR-021). Her madde kaynak segmentlere bağlanır (FR-022).
 */
object InsightExtractor {

    data class Sentence(val text: String, val segmentIds: List<String>)

    data class Result(
        val shortSummary: String,
        val detailedSummary: String,
        val summaryConfidence: Double,
        val insights: List<Insight>
    )

    private val DECISION_MARKERS = listOf(
        "karar verdik", "karar verildi", "karar aldık", "karar alındı", "karara vardık",
        "kararlaştırdık", "kararlaştırıldı", "anlaştık", "mutabık kaldık", "kabul edildi",
        "kabul ettik", "onayladık", "onaylandı", "öyle yapalım", "böyle ilerleyelim"
    )

    private val RISK_MARKERS = listOf(
        "risk", "riskli", "endişe", "tehlike", "sorun olabilir", "sıkıntı olabilir",
        "gecikebilir", "yetişmeyebilir", "problem çıkabilir", "kritik", "aksarsa", "belirsizlik"
    )

    private val NOTE_MARKERS = listOf(
        "önemli", "unutmayalım", "unutma", "not alalım", "not edelim", "altını çiziyorum",
        "dikkat edelim", "dikkat etmemiz", "vurgulamak", "kesinlikle", "mutlaka"
    )

    private val QUESTION_PARTICLES = setOf(
        "mı", "mi", "mu", "mü", "mıyız", "miyiz", "muyuz", "müyüz",
        "mısın", "misin", "musun", "müsün", "mıdır", "midir", "mudur", "müdür"
    )

    fun extract(sentences: List<Sentence>): Result {
        val insights = mutableListOf<Insight>()

        for (s in sentences) {
            val lower = TurkishText.lowercaseTr(s.text)
            val tokens = TurkishText.tokenize(lower)
            when {
                DECISION_MARKERS.any { lower.contains(it) } ->
                    insights.add(make(InsightType.DECISION, s, 0.75))
                RISK_MARKERS.any { lower.contains(it) } ->
                    insights.add(make(InsightType.RISK, s, 0.6))
                NOTE_MARKERS.any { lower.contains(it) } ->
                    insights.add(make(InsightType.IMPORTANT_NOTE, s, 0.6))
                s.text.trimEnd().endsWith("?") || tokens.takeLast(3).any { it in QUESTION_PARTICLES } ->
                    insights.add(make(InsightType.QUESTION, s, 0.55))
            }
        }

        // Tür başına makul üst sınır: en güvenilir/ilk geçenler kalsın
        val capped = insights.groupBy { it.type }.flatMap { (_, list) -> list.take(10) }

        val (short, detailed, conf) = summarize(sentences)
        return Result(short, detailed, conf, capped)
    }

    private fun make(type: InsightType, s: Sentence, conf: Double) = Insight(
        type = type,
        content = TranscriptCleaner.clean(s.text).ifBlank { s.text },
        confidence = conf,
        sourceSegmentIds = s.segmentIds
    )

    /** Çıkarımsal (extractive) özet: içerik sözcüğü sıklığına göre puanlanan cümleler. */
    private fun summarize(sentences: List<Sentence>): Triple<String, String, Double> {
        if (sentences.isEmpty()) return Triple("", "", 0.0)

        val tf = HashMap<String, Int>()
        val tokenized = sentences.map { s ->
            TurkishText.tokenize(s.text).filter { TurkishText.isContentWord(it) }
                .map { TurkishText.lowercaseTr(it) }
                .also { toks -> toks.forEach { tf[it] = (tf[it] ?: 0) + 1 } }
        }

        val scored = sentences.mapIndexed { i, s ->
            val toks = tokenized[i]
            val score = if (toks.isEmpty()) 0.0
            else toks.sumOf { (tf[it] ?: 0).toDouble() } / Math.sqrt(toks.size.toDouble() + 1)
            Triple(i, s, score)
        }.filter { it.third > 0 }

        if (scored.isEmpty()) return Triple(sentences.first().text, "", 0.3)

        fun pick(n: Int): String = scored.sortedByDescending { it.third }.take(n)
            .sortedBy { it.first }
            .joinToString(" ") {
                val c = TranscriptCleaner.clean(it.second.text).ifBlank { it.second.text }
                if (c.endsWith(".")) c else "$c."
            }

        val short = pick(2)
        val detailed = pick(maxOf(3, minOf(8, sentences.size / 5)))
        val conf = if (sentences.size >= 10) 0.65 else 0.45
        return Triple(short, detailed, conf)
    }
}
