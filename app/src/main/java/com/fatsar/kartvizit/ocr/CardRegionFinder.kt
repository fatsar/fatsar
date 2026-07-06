package com.fatsar.kartvizit.ocr

/**
 * Fotoğraftaki açık renkli kartvizit dikdörtgenlerini parlaklık (gri ton)
 * verisinden bulur: Otsu eşikleme ile kart/zemin ayrılır, bağlı bileşenlerle
 * her kartın kapladığı bölge çıkarılır. Böylece kartın **içindeki** beyaz
 * boşluklar (logo ile iletişim bloğu arası gibi) bölünmeye yol açmaz ve her
 * kart, üzerindeki tüm satırlarla birlikte tek parça kalır.
 *
 * Saf Kotlin'dir (Android bağımlılığı yok) ve birim testlenebilir; bitmap
 * çözme işi [CardRegionDetector] tarafındadır.
 */
object CardRegionFinder {

    /** Bir kartın görüntüdeki sınırlayıcı kutusu (piksel, uçlar dahil). */
    data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
    }

    // Kart adayı filtreleri (görüntü boyutuna oranla)
    private const val MIN_AREA_RATIO = 0.008f   // görüntünün en az %0,8'i
    private const val MAX_AREA_RATIO = 0.70f    // tek bileşen görüntünün %70'ini aşamaz
    private const val MIN_DIM_RATIO = 0.05f     // kısa kenar, min(gen,yük)'ün %5'i
    private const val MIN_FILL = 0.45f          // bbox doluluk oranı (eğik kart ~0,7+)
    private const val MIN_ASPECT = 0.30f        // dikey kart + hafif dönme payı
    private const val MAX_ASPECT = 3.4f

    /**
     * @param luminance satır-öncelikli 0..255 parlaklık dizisi (width*height)
     * @return kart adayı bölgeler; kontrast yoksa / kart bulunamazsa boş liste
     */
    fun findCards(luminance: IntArray, width: Int, height: Int): List<Region> {
        if (width <= 2 || height <= 2 || luminance.size < width * height) return emptyList()
        val total = width * height
        val threshold = otsuThreshold(luminance, total)

        val labels = IntArray(total) { -1 }
        val queue = IntArray(total)
        val regions = mutableListOf<Region>()
        val imgArea = total.toLong()
        val minDim = maxOf(6, (minOf(width, height) * MIN_DIM_RATIO).toInt())

        for (start in 0 until total) {
            if (labels[start] != -1 || luminance[start] <= threshold) continue

            // BFS ile parlak bileşeni topla
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = 1
            var minX = start % width; var maxX = minX
            var minY = start / width; var maxY = minY
            var area = 0L

            while (head < tail) {
                val p = queue[head++]
                area++
                val x = p % width
                val y = p / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) {
                    val q = p - 1
                    if (labels[q] == -1 && luminance[q] > threshold) { labels[q] = 1; queue[tail++] = q }
                }
                if (x < width - 1) {
                    val q = p + 1
                    if (labels[q] == -1 && luminance[q] > threshold) { labels[q] = 1; queue[tail++] = q }
                }
                if (y > 0) {
                    val q = p - width
                    if (labels[q] == -1 && luminance[q] > threshold) { labels[q] = 1; queue[tail++] = q }
                }
                if (y < height - 1) {
                    val q = p + width
                    if (labels[q] == -1 && luminance[q] > threshold) { labels[q] = 1; queue[tail++] = q }
                }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val bboxArea = bw.toLong() * bh
            val aspect = bw.toFloat() / bh
            val fill = area.toFloat() / bboxArea
            if (bw >= minDim && bh >= minDim &&
                bboxArea >= (imgArea * MIN_AREA_RATIO).toLong() &&
                bboxArea <= (imgArea * MAX_AREA_RATIO).toLong() &&
                fill >= MIN_FILL &&
                aspect in MIN_ASPECT..MAX_ASPECT
            ) {
                regions.add(Region(minX, minY, maxX, maxY))
            }
        }

        return regions.sortedWith(compareBy({ it.top }, { it.left }))
    }

    /**
     * OCR satırlarını kart bölgelerine dağıtır: merkezini içeren bölge, yoksa
     * merkezi en yakın bölge. Boş gruplar elenir; okuma sırasına göre döner.
     */
    fun group(lines: List<OcrLine>, regions: List<Region>): List<List<OcrLine>> {
        if (regions.isEmpty()) return listOf(lines).filter { it.isNotEmpty() }
        val buckets = List(regions.size) { mutableListOf<OcrLine>() }
        for (line in lines) {
            val cx = line.centerX
            val cy = line.centerY
            var index = regions.indexOfFirst { it.contains(cx, cy) }
            if (index < 0) {
                index = regions.indices.minByOrNull { i ->
                    val dx = (regions[i].centerX - cx).toLong()
                    val dy = (regions[i].centerY - cy).toLong()
                    dx * dx + dy * dy
                } ?: 0
            }
            buckets[index].add(line)
        }
        return buckets.filter { it.isNotEmpty() }
            .sortedWith(compareBy({ it.minOf { l -> l.top } }, { it.minOf { l -> l.left } }))
    }

    /** Klasik Otsu eşiği: kart (parlak) / zemin (koyu) ayrımı için. */
    internal fun otsuThreshold(luminance: IntArray, total: Int): Int {
        val hist = IntArray(256)
        for (i in 0 until total) hist[luminance[i].coerceIn(0, 255)]++

        var sum = 0.0
        for (t in 0..255) sum += t.toDouble() * hist[t]

        var sumB = 0.0
        var wB = 0L
        var best = 127
        var maxVar = -1.0
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0L) continue
            val wF = total - wB
            if (wF == 0L) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                best = t
            }
        }
        return best
    }
}
