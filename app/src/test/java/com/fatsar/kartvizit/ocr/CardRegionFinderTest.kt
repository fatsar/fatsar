package com.fatsar.kartvizit.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRegionFinderTest {

    /** Koyu zemin (ahşap masa benzeri) ızgarası. */
    private fun darkGrid(w: Int, h: Int) = IntArray(w * h) { 60 }

    /** Izgaraya parlak (beyaz kart) dikdörtgen çizer. */
    private fun drawCard(grid: IntArray, gridW: Int, x0: Int, y0: Int, cw: Int, ch: Int) {
        for (y in y0 until y0 + ch) {
            for (x in x0 until x0 + cw) {
                grid[y * gridW + x] = 235
            }
        }
    }

    @Test
    fun `koyu zeminde sekiz kart bulunur (kaydirmali uc sutun)`() {
        // Kullanıcının fotoğrafına benzer: 3 sütun; kenar sütunlarda 3'er,
        // ortada 2 kart; kartlar 56x34, sütunlar arası ~14 px oluk
        val w = 240
        val h = 160
        val grid = darkGrid(w, h)
        intArrayOf(4, 58, 112).forEach { y -> drawCard(grid, w, 8, y, 56, 34) }
        intArrayOf(24, 92).forEach { y -> drawCard(grid, w, 92, y, 56, 34) }
        intArrayOf(4, 58, 112).forEach { y -> drawCard(grid, w, 176, y, 56, 34) }

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(8, regions.size)
    }

    @Test
    fun `kartin icindeki beyaz bosluk bolunmeye yol acmaz`() {
        // Tek büyük kart: içinde metin olmayan alanlar da kartla aynı parlaklıkta
        val w = 120
        val h = 90
        val grid = darkGrid(w, h)
        drawCard(grid, w, 10, 10, 100, 60)

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(1, regions.size)
        val r = regions[0]
        assertTrue(r.contains(60, 15)) // üst blok (logo)
        assertTrue(r.contains(60, 65)) // alt blok (iletişim)
    }

    @Test
    fun `kontrastsiz goruntude bolge bulunmaz`() {
        // Beyaz masa üzerinde beyaz kartlar: her yer parlak -> tek dev bileşen
        val w = 100
        val h = 80
        val grid = IntArray(w * h) { 235 }

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertTrue(regions.size < 2)
    }

    @Test
    fun `kucuk parlamalar kart sayilmaz`() {
        val w = 200
        val h = 120
        val grid = darkGrid(w, h)
        drawCard(grid, w, 10, 10, 60, 36)   // gerçek kart
        drawCard(grid, w, 150, 100, 4, 4)   // parlama/yansıma (çok küçük)

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(1, regions.size)
    }

    @Test
    fun `satirlar bolgelere dogru dagitilir`() {
        val regions = listOf(
            CardRegionFinder.Region(0, 0, 100, 60),
            CardRegionFinder.Region(140, 0, 240, 60)
        )
        val lines = listOf(
            OcrLine("Sol kart adı", left = 10, top = 5, right = 90, bottom = 15),
            OcrLine("sol@kart.com", left = 10, top = 40, right = 90, bottom = 50),
            OcrLine("Sağ kart adı", left = 150, top = 5, right = 230, bottom = 15),
            // Bölge dışına taşan satır: en yakın bölgeye (sağ) atanmalı
            OcrLine("0212 111 22 33", left = 150, top = 62, right = 230, bottom = 72)
        )

        val groups = CardRegionFinder.group(lines, regions)

        assertEquals(2, groups.size)
        assertEquals(2, groups[0].size)
        assertEquals(2, groups[1].size)
        assertTrue(groups[1].any { it.text == "0212 111 22 33" })
    }
}
