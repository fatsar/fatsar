package com.fatsar.toplanti.nlp

import com.fatsar.toplanti.model.InsightType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightExtractorTest {

    private fun s(text: String) = InsightExtractor.Sentence(text, listOf("seg-a"))

    @Test
    fun `karar cumlesi taninir ve kaynaga baglanir`() {
        val result = InsightExtractor.extract(
            listOf(s("fiyatlandırma modelini aylık abonelik olarak belirlemeye karar verdik"))
        )
        val decisions = result.insights.filter { it.type == InsightType.DECISION }
        assertEquals(1, decisions.size)
        assertEquals(listOf("seg-a"), decisions[0].sourceSegmentIds)
    }

    @Test
    fun `soru cumlesi taninir`() {
        val result = InsightExtractor.extract(
            listOf(s("bütçeyi bu hafta onaylayabilir miyiz"))
        )
        assertEquals(1, result.insights.count { it.type == InsightType.QUESTION })
    }

    @Test
    fun `risk cumlesi taninir`() {
        val result = InsightExtractor.extract(
            listOf(s("tedarik gecikirse lansman tarihi risk altında olur"))
        )
        assertEquals(1, result.insights.count { it.type == InsightType.RISK })
    }

    @Test
    fun `onemli not taninir`() {
        val result = InsightExtractor.extract(
            listOf(s("şunu unutmayalım sözleşme yenilemesi eylülde bitiyor"))
        )
        assertEquals(1, result.insights.count { it.type == InsightType.IMPORTANT_NOTE })
    }

    @Test
    fun `ingilizce karar ve soru taninir`() {
        val result = InsightExtractor.extract(
            listOf(
                s("we decided to launch the product in september"),
                s("can we finalize the budget this week")
            ),
            "en"
        )
        assertEquals(1, result.insights.count { it.type == InsightType.DECISION })
        assertEquals(1, result.insights.count { it.type == InsightType.QUESTION })
    }

    @Test
    fun `ozet uretilir ve guven araliktadir`() {
        val sentences = listOf(
            s("mobil uygulama projesinin takvimini konuştuk"),
            s("mobil uygulama tasarımı önümüzdeki ay bitecek"),
            s("bütçe konusunda ek kaynak gerekiyor"),
            s("mobil uygulama testleri için ekip kurulacak")
        )
        val result = InsightExtractor.extract(sentences)
        assertTrue(result.shortSummary.isNotBlank())
        assertTrue(result.summaryConfidence in 0.0..1.0)
    }
}
