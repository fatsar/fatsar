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

    /** Bir kart için 3 satır (ad, e-posta, telefon) üretir. */
    private fun cardLines(index: Int, x0: Int, y0: Int): List<OcrLine> {
        val w = 80
        return listOf(
            line("Ad Soyad $index", x0, y0, x0 + w, y0 + 10),
            line("kisi$index@firma.com", x0, y0 + 14, x0 + w, y0 + 24),
            line("0212 000 00 0$index".take(15), x0, y0 + 28, x0 + w, y0 + 38)
        )
    }

    @Test
    fun `tek sirada on kartvizit ayrilir`() {
        // 10 kart yan yana: kart genişliği 80, aralarında 40 px oluk
        val lines = (0 until 10).flatMap { i -> cardLines(i, x0 = i * 120, y0 = 0) }

        val clusters = CardSegmenter.segment(lines, imageWidth = 1200, imageHeight = 40)

        assertEquals(10, clusters.size)
        clusters.forEach { assertEquals(3, it.size) }
    }

    @Test
    fun `iki satir uc sutun izgara ayrilir`() {
        // 2 satır x 3 sütun = 6 kart
        val lines = mutableListOf<OcrLine>()
        var idx = 0
        for (row in 0 until 2) {
            for (col in 0 until 3) {
                lines += cardLines(idx++, x0 = col * 120, y0 = row * 100)
            }
        }

        val clusters = CardSegmenter.segment(lines, imageWidth = 360, imageHeight = 200)

        assertEquals(6, clusters.size)
    }

    @Test
    fun `kaydirmali uc sutun sekiz kartvizit ayrilir`() {
        // Kullanıcının fotoğrafına benzer: 3 sütun, orta sütun 2 kart (kaydırmalı)
        val lines = mutableListOf<OcrLine>()
        var idx = 0
        intArrayOf(0, 80, 160).forEach { y -> lines += cardLines(idx++, x0 = 0, y0 = y) }
        intArrayOf(40, 200).forEach { y -> lines += cardLines(idx++, x0 = 120, y0 = y) }
        intArrayOf(0, 80, 160).forEach { y -> lines += cardLines(idx++, x0 = 240, y0 = y) }

        val clusters = CardSegmenter.segment(lines, imageWidth = 320, imageHeight = 200)

        assertEquals(8, clusters.size)
    }

    @Test
    fun `egik kartin oluga tasan satiri bolunmeyi engellemez`() {
        // Sol sütun; bir satır eğiklik nedeniyle oluğa doğru taşıyor (x 0..100)
        val left = listOf(
            line("Ahmet Yılmaz", 0, 0, 80, 10),
            line("ahmet@firma.com uzun satir", 0, 14, 100, 24), // oluğa taşan
            line("0212 111 22 33", 0, 28, 80, 38),
            line("Satış Müdürü", 0, 42, 80, 52)
        )
        val right = listOf(
            line("Mehmet Kaya", 120, 0, 200, 10),
            line("mehmet@firma.com", 120, 14, 200, 24),
            line("0216 333 22 11", 120, 28, 200, 38),
            line("Mühendis", 120, 42, 200, 52)
        )

        val clusters = CardSegmenter.segment(left + right, imageWidth = 200, imageHeight = 60)

        assertEquals(2, clusters.size)
    }

    @Test
    fun `kartin ici buyuk boslukla bolunen logo blogu geri birlestirilir`() {
        // Sol kart: logo satırı ile iletişim bloğu arasında büyük iç boşluk
        val leftLogo = line("ACME LOGO", 0, 0, 80, 10)
        val leftBody = listOf(
            line("Ahmet Yılmaz", 0, 60, 80, 70),
            line("ahmet@acme.com", 0, 74, 80, 84),
            line("0212 111 22 33", 0, 88, 80, 98)
        )
        val right = listOf(
            line("Mehmet Kaya", 200, 0, 280, 10),
            line("mehmet@firma.com", 200, 14, 280, 24),
            line("0216 333 22 11", 200, 28, 280, 38),
            line("Mühendis", 200, 42, 280, 52)
        )

        val clusters = CardSegmenter.segment(leftLogo.let { listOf(it) } + leftBody + right)

        // Logo bloğu ayrı kart olmamalı; sol kartla birleşmeli
        assertEquals(2, clusters.size)
        val leftCluster = clusters.first { c -> c.any { it.text == "ACME LOGO" } }
        assertEquals(4, leftCluster.size)
    }

    @Test
    fun `cok satirli logo blogu tek kartta ayri kayit olmaz`() {
        // Öztürk Plastik kartındaki gerçek durum: üstte 4 satırlık firma/logo
        // bloğu, altında büyük boşluğun ardından iletişim bloğu. Logo bloğu 3'ten
        // fazla satır olduğu için eski kural onu "ayrı kart" sanıyordu. İletişim
        // bilgisi yalnızca alt blokta olduğundan üst blok ayrı kayıt olmamalı.
        val logo = listOf(
            line("ÖZTÜRK", 0, 0, 100, 10),
            line("PLASTİK", 0, 12, 100, 22),
            line("AMBALAJ", 0, 24, 100, 34),
            line("SAN TİC", 0, 36, 100, 46)
        )
        val body = listOf(
            line("Yıldıray Öztürk", 0, 70, 100, 80),
            line("info@ozturkplastik.com", 0, 82, 100, 92),
            line("0212 555 44 33", 0, 94, 100, 104)
        )

        val clusters = CardSegmenter.segment(logo + body, imageWidth = 120, imageHeight = 120)

        assertEquals(1, clusters.size)
        assertEquals(7, clusters[0].size)
    }

    @Test
    fun `olukta gurultu kutusu bolunmeyi engellemez`() {
        val left = (0 until 4).map { i -> line("Sol satır $i", 0, i * 14, 80, i * 14 + 10) }
        val right = (0 until 4).map { i -> line("Sag satır $i", 200, i * 14, 280, i * 14 + 10) }
        // Oluğun ortasında tek karakterlik OCR gürültüsü (elenmesi gerekir)
        val noise = line("·", 130, 20, 140, 30)

        val clusters = CardSegmenter.segment(left + noise + right, imageWidth = 300, imageHeight = 60)

        assertEquals(2, clusters.size)
    }
}
