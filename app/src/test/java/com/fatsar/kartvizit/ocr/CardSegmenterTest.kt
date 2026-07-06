package com.fatsar.kartvizit.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSegmenterTest {

    /** Kutulu satır üretmek için yardımcı. */
    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLine(text, height = (bottom - top).toFloat(), left = left, top = top, right = right, bottom = bottom)

    @Test
    fun `yan yana iki kartvizit ayrilir`() {
        val lines = listOf(
            // Sol kart (x: 10..230)
            line("Ahmet Yılmaz", 10, 10, 210, 40),
            line("Satış Müdürü", 10, 50, 200, 75),
            line("ahmet@firma-a.com", 10, 80, 230, 105),
            line("0212 555 44 33", 10, 110, 230, 135),
            // Sağ kart (x: 520..740) — arada ~290 px oluk
            line("Mehmet Kaya", 520, 12, 720, 42),
            line("Mühendis", 520, 50, 700, 75),
            line("mehmet@firma-b.com", 520, 80, 740, 105),
            line("0216 333 22 11", 520, 110, 740, 135)
        )

        val clusters = CardSegmenter.segment(lines, imageWidth = 800, imageHeight = 150)

        assertEquals(2, clusters.size)
        assertEquals(4, clusters[0].size)
        assertEquals(4, clusters[1].size)

        val names = clusters.map { CardTextParser.parse(it).name }
        assertTrue(names.contains("Ahmet Yılmaz"))
        assertTrue(names.contains("Mehmet Kaya"))
    }

    @Test
    fun `tek kartvizit bolunmez`() {
        val lines = listOf(
            line("Ahmet Yılmaz", 10, 10, 210, 40),
            line("Satış Müdürü", 10, 50, 200, 75),
            line("Yıldız A.Ş.", 10, 90, 220, 115),
            line("ahmet@yildiz.com", 10, 130, 230, 155),
            line("0212 555 44 33", 10, 170, 230, 195),
            line("www.yildiz.com", 10, 210, 220, 235)
        )

        val clusters = CardSegmenter.segment(lines, imageWidth = 260, imageHeight = 250)

        assertEquals(1, clusters.size)
        assertEquals(6, clusters[0].size)
    }

    @Test
    fun `az sayida satir tek kart olarak birakilir`() {
        val lines = listOf(
            line("Ahmet Yılmaz", 10, 10, 210, 40),
            line("0212 555 44 33", 600, 10, 800, 40)
        )
        val clusters = CardSegmenter.segment(lines, imageWidth = 820, imageHeight = 50)
        assertEquals(1, clusters.size)
    }

    @Test
    fun `kutu bilgisi yoksa tek kart dondurur`() {
        val lines = (1..8).map { OcrLine("Satır $it") } // hepsi 0 kutulu
        val clusters = CardSegmenter.segment(lines)
        assertEquals(1, clusters.size)
    }
}
