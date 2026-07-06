package com.fatsar.kartvizit.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRegionFinderTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Ahşap masa benzeri kahverengi zemin. */
    private fun woodGrid(w: Int, h: Int) = IntArray(w * h) { rgb(150, 120, 80) }

    /** Izgaraya beyaz kart dikdörtgeni çizer. */
    private fun drawCard(grid: IntArray, gridW: Int, x0: Int, y0: Int, cw: Int, ch: Int, color: Int = rgb(235, 235, 235)) {
        for (y in y0 until y0 + ch) {
            for (x in x0 until x0 + cw) {
                grid[y * gridW + x] = color
            }
        }
    }

    @Test
    fun `ahsap zeminde sekiz kart bulunur (kaydirmali uc sutun)`() {
        val w = 240
        val h = 160
        val grid = woodGrid(w, h)
        intArrayOf(4, 58, 112).forEach { y -> drawCard(grid, w, 8, y, 56, 34) }
        intArrayOf(24, 92).forEach { y -> drawCard(grid, w, 92, y, 56, 34) }
        intArrayOf(4, 58, 112).forEach { y -> drawCard(grid, w, 176, y, 56, 34) }

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(8, regions.size)
    }

    @Test
    fun `parlak ahsap damari kartlari birlestirmez`() {
        // Galeri hatasının kök nedeni: kartlar arasından geçen açık renkli
        // zemin damarı, parlaklık eşiğinde köprü kurup her şeyi tek parça
        // gösteriyordu. Renk (kromatiklik) modeli damarı zemin saymalı.
        val w = 240
        val h = 100
        val grid = woodGrid(w, h)
        // Kartların arasından ve altından geçen parlak damar şeridi
        for (y in 44 until 52) {
            for (x in 0 until w) grid[y * w + x] = rgb(200, 170, 130)
        }
        drawCard(grid, w, 10, 20, 70, 50)
        drawCard(grid, w, 160, 20, 70, 50)

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(2, regions.size)
    }

    @Test
    fun `kartin icindeki beyaz bosluk bolunmeye yol acmaz`() {
        val w = 120
        val h = 90
        val grid = woodGrid(w, h)
        drawCard(grid, w, 10, 10, 100, 60)

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(1, regions.size)
        assertTrue(regions[0].contains(60, 15))
        assertTrue(regions[0].contains(60, 65))
    }

    @Test
    fun `kontrastsiz goruntude bolge bulunmaz`() {
        // Beyaz masa üzerinde beyaz kartlar: zemin modeli her şeyi kapsar
        val w = 100
        val h = 80
        val grid = IntArray(w * h) { rgb(235, 235, 235) }

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertTrue(regions.size < 2)
    }

    @Test
    fun `kucuk parlamalar kart sayilmaz`() {
        val w = 200
        val h = 120
        val grid = woodGrid(w, h)
        drawCard(grid, w, 10, 10, 60, 36)
        drawCard(grid, w, 150, 100, 3, 3, rgb(255, 255, 255)) // yansıma noktası

        val regions = CardRegionFinder.findCards(grid, w, h)

        assertEquals(1, regions.size)
    }

    // ---- refine: bölge + OCR satırı uzlaştırma ----

    private fun textLine(text: String, x0: Int, y0: Int, x1: Int, y1: Int) =
        OcrLine(text, height = (y1 - y0).toFloat(), left = x0, top = y0, right = x1, bottom = y1)

    @Test
    fun `golgeyle ikiye bolunen kart tek karta birlesir`() {
        // Aynı kartın iki yarısı (arada 3 px'lik gölge çizgisi) + uzak ikinci kart
        val candidates = listOf(
            CardRegionFinder.Region(10, 10, 60, 80),
            CardRegionFinder.Region(64, 10, 114, 80),
            CardRegionFinder.Region(200, 10, 300, 80)
        )
        val lines = listOf(
            textLine("Ahmet Yılmaz", 15, 15, 55, 25),
            textLine("ahmet@firma.com", 66, 30, 112, 40),
            textLine("Mehmet Kaya", 205, 15, 295, 25),
            textLine("0216 333 22 11", 205, 40, 295, 50)
        )

        val groups = CardRegionFinder.refine(candidates, lines)

        assertEquals(2, groups.size)
        assertTrue(groups[0].any { it.text == "Ahmet Yılmaz" })
        assertTrue(groups[0].any { it.text == "ahmet@firma.com" })
    }

    @Test
    fun `metinsiz parlama bolgesi elenir`() {
        val candidates = listOf(
            CardRegionFinder.Region(10, 10, 110, 80),
            CardRegionFinder.Region(300, 200, 360, 240) // parlama: satır yok
        )
        val lines = listOf(
            textLine("Ahmet Yılmaz", 15, 15, 105, 25),
            textLine("0212 111 22 33", 15, 40, 105, 50)
        )

        val groups = CardRegionFinder.refine(candidates, lines)

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
    }

    @Test
    fun `tek bolge cikan bitisik kart cifti metinden ikiye ayrilir`() {
        // İki kart bitişik durduğu için tek aday bölge çıktı; her iki tarafta
        // da güçlü kart içeriği (telefon/e-posta) olduğundan ikiye bölünmeli
        val candidates = listOf(CardRegionFinder.Region(0, 0, 300, 60))
        val left = listOf(
            textLine("Ahmet Yılmaz", 5, 5, 95, 15),
            textLine("ahmet@a.com", 5, 22, 95, 32),
            textLine("0212 111 22 33", 5, 40, 95, 50)
        )
        val right = listOf(
            textLine("Mehmet Kaya", 205, 5, 295, 15),
            textLine("mehmet@b.com", 205, 22, 295, 32),
            textLine("0216 333 22 11", 205, 40, 295, 50)
        )

        val groups = CardRegionFinder.refine(candidates, left + right)

        assertEquals(2, groups.size)
    }

    @Test
    fun `zayif parca alt bolmede ayri kart yapilmaz`() {
        // Kartın logo bloğu (iletişimsiz) alt bölmede ayrı kart olmamalı
        val candidates = listOf(CardRegionFinder.Region(0, 0, 120, 120))
        val lines = listOf(
            textLine("ACME LOGO", 10, 5, 110, 15),
            textLine("Ahmet Yılmaz", 10, 70, 110, 80),
            textLine("ahmet@acme.com", 10, 88, 110, 98),
            textLine("0212 111 22 33", 10, 105, 110, 115)
        )

        val groups = CardRegionFinder.refine(candidates, lines)

        assertEquals(1, groups.size)
        assertEquals(4, groups[0].size)
    }

    @Test
    fun `satirlar bolgelere dogru dagitilir`() {
        val regions = listOf(
            CardRegionFinder.Region(0, 0, 100, 60),
            CardRegionFinder.Region(140, 0, 240, 60)
        )
        val lines = listOf(
            textLine("Sol kart adı", 10, 5, 90, 15),
            textLine("sol@kart.com", 10, 40, 90, 50),
            textLine("Sağ kart adı", 150, 5, 230, 15),
            textLine("0212 111 22 33", 150, 62, 230, 72) // bölge dışı: en yakına
        )

        val groups = CardRegionFinder.group(lines, regions)

        assertEquals(2, groups.size)
        assertTrue(groups[1].any { it.text == "0212 111 22 33" })
    }
}
