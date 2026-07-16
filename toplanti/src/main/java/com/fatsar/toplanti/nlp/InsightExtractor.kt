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

    // İngilizce karşılıklar
    private val EN_DECISION_MARKERS = listOf(
        "we decided", "decided to", "we agreed", "agreed to", "agreed on", "approved",
        "let's go with", "we will go with", "final decision", "settled on", "signed off"
    )
    private val EN_RISK_MARKERS = listOf(
        "risk", "risky", "concern", "worried", "might be delayed", "could be delayed",
        "blocker", "critical", "danger", "problem if", "uncertainty"
    )
    private val EN_NOTE_MARKERS = listOf(
        "important", "don't forget", "dont forget", "keep in mind", "note that",
        "remember that", "make sure", "highlight", "key point", "definitely"
    )
    private val EN_QUESTION_STARTS = setOf("what", "when", "who", "why", "how", "where", "which")
    private val EN_QUESTION_AUX = setOf("can", "could", "should", "shall", "do", "does", "did", "are", "is", "will", "would")
    private val EN_QUESTION_SUBJECTS = setOf("we", "you", "i", "it", "they", "anyone", "there")

    fun extract(sentences: List<Sentence>, language: String = "tr"): Result {
        val insights = mutableListOf<Insight>()
        val en = language == "en"
        val decisionMarkers = if (en) EN_DECISION_MARKERS else DECISION_MARKERS
        val riskMarkers = if (en) EN_RISK_MARKERS else RISK_MARKERS
        val noteMarkers = if (en) EN_NOTE_MARKERS else NOTE_MARKERS

        for (s in sentences) {
            val lower = TurkishText.lowercaseTr(s.text)
            val tokens = TurkishText.tokenize(lower)
            when {
                decisionMarkers.any { lower.contains(it) } ->
                    insights.add(make(InsightType.DECISION, s, 0.75))
                riskMarkers.any { lower.contains(it) } ->
                    insights.add(make(InsightType.RISK, s, 0.6))
                noteMarkers.any { lower.contains(it) } ->
                    insights.add(make(InsightType.IMPORTANT_NOTE, s, 0.6))
                isQuestion(s.text, tokens, en) ->
                    insights.add(make(InsightType.QUESTION, s, 0.55))
            }
        }

        // Tür başına makul üst sınır: en güvenilir/ilk geçenler kalsın
        val capped = insights.groupBy { it.type }.flatMap { (_, list) -> list.take(10) }

        val (short, detailed, conf) = summarize(sentences, language)
        return Result(short, detailed, conf, capped)
    }

    private fun isQuestion(text: String, tokens: List<String>, en: Boolean): Boolean {
        if (text.trimEnd().endsWith("?")) return true
        return if (en) {
            tokens.firstOrNull() in EN_QUESTION_STARTS ||
                (tokens.size >= 2 && tokens[0] in EN_QUESTION_AUX && tokens[1] in EN_QUESTION_SUBJECTS)
        } else {
            tokens.takeLast(3).any { it in QUESTION_PARTICLES }
        }
    }

    private fun make(type: InsightType, s: Sentence, conf: Double) = Insight(
        type = type,
        content = TranscriptCleaner.clean(s.text).ifBlank { s.text },
        confidence = conf,
        sourceSegmentIds = s.segmentIds
    )

    /** Çıkarımsal (extractive) özet: içerik sözcüğü sıklığına göre puanlanan cümleler. */
    private fun summarize(sentences: List<Sentence>, language: String): Triple<String, String, Double> {
        if (sentences.isEmpty()) return Triple("", "", 0.0)

        val tf = HashMap<String, Int>()
        val tokenized = sentences.map { s ->
            TurkishText.tokenize(s.text).filter { TurkishText.isContentWord(it, language) }
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
