package com.fatsar.kartvizit.ocr

import kotlin.math.max

/**
 * Tek bir fotoğraftaki OCR satırlarını konumlarına göre kümeleyerek birden
 * fazla kartviziti ayırır. Kartlar arasındaki boşluk ("oluk"), satır
 * kutularının izdüşümünde bir vadi olarak görünür.
 *
 * Klasik **XY-cut** yaklaşımı, gerçek fotoğraflara dayanıklı hale getirildi:
 * - Kutular projeksiyondan önce biraz **içeri çekilir** (erosion); böylece
 *   hafif eğik/döndürülmüş kartların komşu oluğa taşan kutuları oluğu
 *   kapatmaz.
 * - Oluk, "hiç kutu yok" yerine **düşük yoğunluklu vadi** olarak aranır;
 *   böylece oluğu geçen birkaç hatalı/gürültü kutu bölünmeyi engellemez.
 * - Eşik, görüntü boyutuna değil **satır yüksekliğine** göredir (ölçekten
 *   bağımsız).
 * - En belirgin oluğun bulunduğu eksende o eksendeki **tüm** oluklardan aynı
 *   anda bölünür (10 kart yan yana ise tek adımda 10 sütuna); kart sayısında
 *   sınır yoktur.
 */
object CardSegmenter {

    private const val MAX_DEPTH = 24

    /** Oluk, satır yüksekliğinin bu katından genişse kartlar arası sayılır. */
    private const val GAP_FACTOR = 2.0f

    /** Projeksiyon öncesi her kutu, satır yüksekliğinin bu kadarı içeri çekilir. */
    private const val ERODE_FACTOR = 0.5f

    /** Vadi eşiği: yoğunluk, tepe değerinin bu oranından düşükse "boş" sayılır. */
    private const val VALLEY_RATIO = 0.06f

    private val ALNUM = Regex("""[\p{L}\p{Nd}]""")

    private data class Gap(val start: Int, val end: Int) {
        val size: Int get() = end - start
        val mid: Int get() = (start + end) / 2
    }

    @Suppress("UNUSED_PARAMETER")
    fun segment(lines: List<OcrLine>, imageWidth: Int = 0, imageHeight: Int = 0): List<List<OcrLine>> {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.size < 4 || !hasBoxes(usable)) return listOf(usable).filter { it.isNotEmpty() }

        // Yalnızca "sinyal" satırları (en az iki harf/rakam ve geçerli kutu)
        // projeksiyonda kullanılır; tek karakterlik OCR gürültüsü oluğu kapatmaz.
        val signal = usable.filter {
            alnumCount(it.text) >= 2 && it.right > it.left && it.bottom > it.top
        }
        if (signal.size < 4) return listOf(usable)

        val lineHeight = medianLineHeight(signal)
        val erode = (lineHeight * ERODE_FACTOR).toInt()
        val minGap = max((lineHeight * GAP_FACTOR).toInt(), 1)

        val clusters = xyCut(signal, minGap, erode, 0).map { it.toMutableList() }.toMutableList()
        if (clusters.size > 1) {
            // Sinyal dışı satırları (kısa etiketler, gürültü) en yakın kümeye ekle
            val residual = usable.filter { line -> clusters.none { it.contains(line) } }
            residual.forEach { nearestCluster(clusters, it.centerX, it.centerY).add(it) }
            // Kartın iç boşluğundan kopan zayıf parçaları geri birleştir
            mergeWeakClusters(clusters)
        }
        return if (clusters.size > 1) {
            clusters.sortedWith(compareBy({ it.minOf { l -> l.top } }, { it.minOf { l -> l.left } }))
        } else {
            listOf(usable)
        }
    }

    /**
     * Gerçek bir kartvizitte hemen her zaman telefon ya da e-posta bulunur.
     * Bunlardan yoksun küçük kümeler (ör. kartın içindeki büyük beyaz boşluk
     * yüzünden ayrı düşen logo/isim bloğu) en yakın kümeye geri birleştirilir.
     */
    private fun mergeWeakClusters(clusters: MutableList<MutableList<OcrLine>>) {
        while (clusters.size > 1) {
            val weakIndex = clusters.indexOfFirst { isWeak(it) }
            if (weakIndex < 0) return
            val weak = clusters.removeAt(weakIndex)
            val cx = weak.sumOf { it.centerX } / weak.size
            val cy = weak.sumOf { it.centerY } / weak.size
            nearestCluster(clusters, cx, cy).addAll(weak)
        }
    }

    private fun isWeak(cluster: List<OcrLine>): Boolean {
        if (cluster.size < 2) return true
        if (cluster.size > 3) return false
        return cluster.none { line ->
            line.text.contains('@') || line.text.count { it.isDigit() } >= 7
        }
    }

    private fun nearestCluster(
        clusters: List<MutableList<OcrLine>>,
        x: Int,
        y: Int
    ): MutableList<OcrLine> = clusters.minByOrNull { cluster ->
        val cx = cluster.sumOf { it.centerX } / cluster.size
        val cy = cluster.sumOf { it.centerY } / cluster.size
        val dx = (cx - x).toLong()
        val dy = (cy - y).toLong()
        dx * dx + dy * dy
    }!!

    private fun xyCut(lines: List<OcrLine>, minGap: Int, erode: Int, depth: Int): List<List<OcrLine>> {
        if (lines.size <= 1 || depth >= MAX_DEPTH) return listOf(lines)

        val xValleys = valleys(lines, minGap, erode, { it.left }, { it.right })
        val yValleys = valleys(lines, minGap, erode, { it.top }, { it.bottom })
        val xMax = xValleys.maxOfOrNull { it.size } ?: 0
        val yMax = yValleys.maxOfOrNull { it.size } ?: 0

        return when {
            xValleys.isNotEmpty() && xMax >= yMax ->
                splitAt(lines, xValleys.map { it.mid }) { it.centerX }
                    .flatMap { xyCut(it, minGap, erode, depth + 1) }
            yValleys.isNotEmpty() ->
                splitAt(lines, yValleys.map { it.mid }) { it.centerY }
                    .flatMap { xyCut(it, minGap, erode, depth + 1) }
            else -> listOf(lines)
        }
    }

    /**
     * Verilen eksende, kutuların (içeri çekilmiş) izdüşümündeki düşük yoğunluklu
     * iç vadileri bulur. Her x/y için o konumu kaplayan satır sayısı hesaplanır;
     * sayı tepe değerinin küçük bir oranından düşükse orası "boş" kabul edilir.
     */
    private fun valleys(
        lines: List<OcrLine>,
        minGap: Int,
        erode: Int,
        lo: (OcrLine) -> Int,
        hi: (OcrLine) -> Int
    ): List<Gap> {
        val intervals = lines.map {
            val a = lo(it) + erode
            val b = hi(it) - erode
            if (b > a) a to b else {
                val m = (lo(it) + hi(it)) / 2
                m to (m + 1)
            }
        }
        val points = (intervals.map { it.first } + intervals.map { it.second })
            .distinct().sorted()
        if (points.size < 2) return emptyList()

        val counts = IntArray(points.size - 1)
        for ((a, b) in intervals) {
            var i = points.binarySearch(a)
            var j = points.binarySearch(b)
            if (i < 0) i = -i - 1
            if (j < 0) j = -j - 1
            for (k in i until j) counts[k]++
        }

        val peak = counts.maxOrNull() ?: 0
        if (peak == 0) return emptyList()
        val emptyThreshold = (peak * VALLEY_RATIO).toInt()

        val gaps = mutableListOf<Gap>()
        var k = 0
        while (k < counts.size) {
            if (counts[k] <= emptyThreshold) {
                val start = points[k]
                var end = points[k + 1]
                var kk = k
                while (kk < counts.size && counts[kk] <= emptyThreshold) {
                    end = points[kk + 1]
                    kk++
                }
                // Yalnızca iç vadiler (iki yanında da metin var); kenar boşlukları değil
                val interior = k > 0 && kk < counts.size
                if (interior && end - start >= minGap) gaps.add(Gap(start, end))
                k = kk
            } else {
                k++
            }
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
        // Kutunun kısa kenarı = yazı boyutu; dik (90° dönmüş) satırlarda da doğru
        val heights = lines
            .map { minOf(it.bottom - it.top, it.right - it.left) }
            .filter { it > 0 }
            .sorted()
        if (heights.isEmpty()) return 1f
        return heights[heights.size / 2].toFloat()
    }

    private fun alnumCount(text: String): Int = ALNUM.findAll(text).count()

    private fun hasBoxes(lines: List<OcrLine>): Boolean =
        lines.any { it.right > it.left && it.bottom > it.top }
}
