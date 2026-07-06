package com.fatsar.kartvizit.ocr

import kotlin.math.max

/**
 * Tek bir fotoğraftaki OCR satırlarını konumlarına göre kümeleyerek birden
 * fazla kartviziti ayırır. Kartlar arasındaki boşluk ("oluk"), satır
 * kutularının kapsamında bir boşluk olarak görünür.
 *
 * Klasik **XY-cut** yaklaşımı kullanılır: her adımda yatay ve dikey
 * oluklara bakılır, en belirgin oluğun bulunduğu eksende o eksendeki
 * **tüm** geçerli oluklardan aynı anda bölünür (10 kart yan yana ise tek
 * adımda 10 sütuna ayrılır) ve her parça özyinelemeli olarak diğer eksende
 * bölünür. Böylece kart sayısında sınır yoktur.
 *
 * Eşik, görüntü boyutuna değil **satır yüksekliğine** göre belirlenir
 * (ölçekten bağımsız): kart içindeki satır aralığı bir satır yüksekliğinden
 * küçük, kartlar arası oluk ise belirgin biçimde büyüktür.
 */
object CardSegmenter {

    private const val MAX_DEPTH = 24

    /** Oluk, satır yüksekliğinin bu katından büyükse kartlar arası sayılır. */
    private const val GAP_FACTOR = 1.7f

    private val LETTER_OR_DIGIT = Regex("""[\p{L}\p{Nd}]""")

    private data class Gap(val start: Int, val end: Int) {
        val size: Int get() = end - start
        val mid: Int get() = (start + end) / 2
    }

    /**
     * @param imageWidth/imageHeight kullanılmıyor (geriye dönük imza uyumu için
     * korunur); eşik artık satır yüksekliğinden türetilir.
     */
    @Suppress("UNUSED_PARAMETER")
    fun segment(lines: List<OcrLine>, imageWidth: Int = 0, imageHeight: Int = 0): List<List<OcrLine>> {
        val usable = lines.filter { it.text.isNotBlank() }
        // Çok az satır ya da kutu bilgisi yoksa çoklu karta ayırmaya çalışma
        if (usable.size < 4 || !hasBoxes(usable)) return listOf(usable).filter { it.isNotEmpty() }

        val lineHeight = medianLineHeight(usable)
        val minGap = max((lineHeight * GAP_FACTOR).toInt(), 1)

        val cells = xyCut(usable, minGap, 0)
            .filter { cluster -> cluster.any { LETTER_OR_DIGIT.containsMatchIn(it.text) } }

        val result = if (cells.size > 1) cells else listOf(usable)
        // Okuma sırası: üstten alta, soldan sağa
        return result.sortedWith(
            compareBy({ it.minOf { l -> l.top } }, { it.minOf { l -> l.left } })
        )
    }

    private fun xyCut(lines: List<OcrLine>, minGap: Int, depth: Int): List<List<OcrLine>> {
        if (lines.size <= 1 || depth >= MAX_DEPTH) return listOf(lines)

        val xGaps = axisGaps(lines) { it.left to it.right }.filter { it.size >= minGap }
        val yGaps = axisGaps(lines) { it.top to it.bottom }.filter { it.size >= minGap }
        val xMax = xGaps.maxOfOrNull { it.size } ?: 0
        val yMax = yGaps.maxOfOrNull { it.size } ?: 0

        // En belirgin oluğun bulunduğu ekseni seç; o eksendeki tüm oluklardan böl
        return when {
            xGaps.isNotEmpty() && xMax >= yMax ->
                splitAt(lines, xGaps.map { it.mid }) { it.centerX }
                    .flatMap { xyCut(it, minGap, depth + 1) }
            yGaps.isNotEmpty() ->
                splitAt(lines, yGaps.map { it.mid }) { it.centerY }
                    .flatMap { xyCut(it, minGap, depth + 1) }
            else -> listOf(lines)
        }
    }

    /**
     * Verilen eksende satır kutularının birleşik kapsamındaki boşlukları
     * (oluklar) bulur. Kutuları [start,end] aralıklarına indirger, birleştirir
     * ve ardışık kapalı bölümler arasındaki boşlukları döndürür.
     */
    private fun axisGaps(lines: List<OcrLine>, selector: (OcrLine) -> Pair<Int, Int>): List<Gap> {
        val intervals = lines.map(selector).sortedBy { it.first }
        var coveredEnd = intervals.first().second
        val gaps = mutableListOf<Gap>()
        for ((start, end) in intervals.drop(1)) {
            if (start > coveredEnd) gaps.add(Gap(coveredEnd, start))
            if (end > coveredEnd) coveredEnd = end
        }
        return gaps
    }

    /** Satırları, verilen kesim konumlarına göre kova kova ayırır. */
    private fun splitAt(
        lines: List<OcrLine>,
        cuts: List<Int>,
        center: (OcrLine) -> Int
    ): List<List<OcrLine>> {
        if (cuts.isEmpty()) return listOf(lines)
        val sorted = cuts.sorted()
        return lines
            .groupBy { line -> sorted.count { it < center(line) } }
            .toSortedMap()
            .values
            .toList()
    }

    private fun medianLineHeight(lines: List<OcrLine>): Float {
        val heights = lines.map { it.bottom - it.top }.filter { it > 0 }.sorted()
        if (heights.isEmpty()) return 1f
        return heights[heights.size / 2].toFloat()
    }

    private fun hasBoxes(lines: List<OcrLine>): Boolean =
        lines.any { it.right > it.left && it.bottom > it.top }
}
