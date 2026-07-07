package com.fatsar.kartvizit.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fotoğraftaki kartvizit bölgelerini bulur.
 *
 * Zemin, parlaklıkla değil **renk kimliğiyle** (kromatiklik) modellenir:
 * fotoğrafın kenar çerçevesinden zeminin renk oranları (r/toplam, g/toplam)
 * ve parlaklığı örneklenir. Ahşap zeminin parlak damarları ve gölgeli
 * bölümleri aynı renk ailesinde kaldığından zemin sayılır; beyaz/renkli
 * kartlar renk oranlarıyla ayrışır. Böylece parlak zemin çizgilerinin
 * kartlar arasında köprü kurup hepsini tek parça göstermesi önlenir.
 *
 * Ardından ince köprüleri kesen erozyon uygulanır ve bağlı bileşenlerle
 * kart adayları çıkarılır. [refine], adayları OCR satırlarıyla uzlaştırır:
 * gölgeyle ikiye bölünen kart parçalarını birleştirir, metin içermeyen
 * parlama/yansıma bölgelerini eler, bitişik durduğu için tek bölge çıkan
 * kart çiftlerini metin düzeninden ikiye ayırır.
 *
 * Saf Kotlin'dir (Android bağımlılığı yok); bitmap çözme işi
 * [CardRegionDetector] tarafındadır.
 */
object CardRegionFinder {

    /** Bir kartın görüntüdeki sınırlayıcı kutusu (piksel, uçlar dahil). */
    data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
    }

    // Zemin modeli toleransları (kenar örneklerinin MAD'ine göre büyür)
    private const val CHROMA_TOL_BASE = 10
    private const val LUM_TOL_BASE = 60

    // Kart adayı filtreleri
    private const val MIN_AREA_RATIO = 0.004f
    private const val MAX_AREA_RATIO = 0.75f
    private const val MIN_DIM_RATIO = 0.04f
    private const val MIN_FILL = 0.30f

    /**
     * @param argb satır-öncelikli paketlenmiş ARGB/RGB piksel dizisi
     * @return kart adayı bölgeler; zemin/kart ayrımı yapılamazsa boş liste
     */
    fun findCards(argb: IntArray, width: Int, height: Int): List<Region> {
        val total = width * height
        if (width <= 8 || height <= 8 || argb.size < total) return emptyList()

        // 1) Kenar çerçevesinden zemin renk modeli
        val margin = max(2, min(width, height) / 50)
        val crs = ArrayList<Int>()
        val cgs = ArrayList<Int>()
        val lums = ArrayList<Int>()
        var y = 0
        while (y < height) {
            val edgeRow = y < margin || y >= height - margin
            var x = 0
            while (x < width) {
                if (edgeRow || x < margin || x >= width - margin) {
                    val p = argb[y * width + x]
                    val r = p ushr 16 and 0xFF
                    val g = p ushr 8 and 0xFF
                    val b = p and 0xFF
                    val sum = r + g + b + 1
                    crs.add(255 * r / sum)
                    cgs.add(255 * g / sum)
                    lums.add((r * 299 + g * 587 + b * 114) / 1000)
                }
                x += 2
            }
            y += 2
        }
        if (crs.size < 16) return emptyList()
        val medCr = median(crs)
        val medCg = median(cgs)
        val medLum = median(lums)
        val tolCr = max(CHROMA_TOL_BASE, 3 * mad(crs, medCr))
        val tolCg = max(CHROMA_TOL_BASE, 3 * mad(cgs, medCg))
        val tolLum = max(LUM_TOL_BASE, 6 * mad(lums, medLum))

        // 2) Ön plan maskesi: zemin renk ailesinden sapan ya da belirgin
        //    biçimde daha parlak pikseller
        val mask = BooleanArray(total)
        for (i in 0 until total) {
            val p = argb[i]
            val r = p ushr 16 and 0xFF
            val g = p ushr 8 and 0xFF
            val b = p and 0xFF
            val sum = r + g + b
            if (sum < 90) continue // çok karanlık: derin gölge/zemin
            val cr = 255 * r / (sum + 1)
            val cg = 255 * g / (sum + 1)
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            mask[i] = abs(cr - medCr) > tolCr || abs(cg - medCg) > tolCg ||
                (lum - medLum) > tolLum
        }

        // 3) Erozyon: ince köprüleri (kenar yumuşaması, dar taşmalar) keser
        val erosion = max(2, min(width, height) / 160)
        erode(mask, width, height, erosion)

        // 4) Bağlı bileşenler
        val labels = IntArray(total) { -1 }
        val queue = IntArray(total)
        val regions = mutableListOf<Region>()
        val imgArea = total.toLong()
        val minDim = max(4, (min(width, height) * MIN_DIM_RATIO).toInt())
        val pad = erosion + 1

        for (start in 0 until total) {
            if (labels[start] != -1 || !mask[start]) continue
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
                val px = p % width
                val py = p / width
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                if (px > 0 && labels[p - 1] == -1 && mask[p - 1]) { labels[p - 1] = 1; queue[tail++] = p - 1 }
                if (px < width - 1 && labels[p + 1] == -1 && mask[p + 1]) { labels[p + 1] = 1; queue[tail++] = p + 1 }
                if (py > 0 && labels[p - width] == -1 && mask[p - width]) { labels[p - width] = 1; queue[tail++] = p - width }
                if (py < height - 1 && labels[p + width] == -1 && mask[p + width]) { labels[p + width] = 1; queue[tail++] = p + width }
            }

            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val bboxArea = bw.toLong() * bh
            val fill = area.toFloat() / bboxArea
            if (bw >= minDim && bh >= minDim &&
                bboxArea >= (imgArea * MIN_AREA_RATIO).toLong() &&
                bboxArea <= (imgArea * MAX_AREA_RATIO).toLong() &&
                fill >= MIN_FILL
            ) {
                regions.add(
                    Region(
                        max(0, minX - pad),
                        max(0, minY - pad),
                        min(width - 1, maxX + pad),
                        min(height - 1, maxY + pad)
                    )
                )
            }
        }

        return regions.sortedWith(compareBy({ it.top }, { it.left }))
    }

    /**
     * Kart adaylarını OCR satırlarıyla uzlaştırıp satır gruplarını döndürür.
     * Bağlı bileşenler her kartı zaten tek parça verdiğinden burada
     * **birleştirme/alt-bölme yapılmaz** (aksi hâlde kaydırmalı yerleşimde
     * komşu kartlar yanlışlıkla birleşir ya da tek kart ikiye bölünürdü):
     *
     * 1. Her satır, merkezini içeren bölgeye; yoksa en yakın bölgeye atanır.
     * 2. Hiç satır düşmeyen bölgeler elenir (metinsiz parlama/yansıma/nesne).
     * 3. Tümüyle başka bir bölgenin içinde kalan küçük parça bölgeler, o
     *    bölgeyle birleştirilir (tek kartın koptuğu kesin durum).
     */
    fun refine(candidates: List<Region>, lines: List<OcrLine>): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        if (candidates.isEmpty()) return listOf(lines)

        // 3) İçte kalan parça bölgeleri kapsayana katmak için indeks eşlemesi
        val target = IntArray(candidates.size) { it }
        for (i in candidates.indices) {
            for (j in candidates.indices) {
                if (i != j && contains(candidates[j], candidates[i])) {
                    target[i] = j
                    break
                }
            }
        }

        // 1) Satırları (kapsayıcı) bölgelere dağıt
        val buckets = List(candidates.size) { mutableListOf<OcrLine>() }
        for (line in lines) {
            val cx = line.centerX
            val cy = line.centerY
            var index = candidates.indexOfFirst { it.contains(cx, cy) }
            if (index < 0) {
                index = candidates.indices.minByOrNull { i ->
                    val dx = (candidates[i].centerX - cx).toLong()
                    val dy = (candidates[i].centerY - cy).toLong()
                    dx * dx + dy * dy
                } ?: 0
            }
            buckets[target[index]].add(line)
        }

        // 2) Metinsiz bölgeleri ele, okuma sırasına göre döndür
        return candidates.indices
            .filter { target[it] == it && buckets[it].isNotEmpty() }
            .map { buckets[it] }
            .sortedWith(compareBy({ it.minOf { l -> l.top } }, { it.minOf { l -> l.left } }))
    }

    /** [outer], [inner]'ı büyük ölçüde kapsıyor mu (en az %85 alan içinde)? */
    private fun contains(outer: Region, inner: Region): Boolean {
        val ix = max(0, min(outer.right, inner.right) - max(outer.left, inner.left))
        val iy = max(0, min(outer.bottom, inner.bottom) - max(outer.top, inner.top))
        val interArea = ix.toLong() * iy
        val innerArea = inner.width.toLong() * inner.height
        return innerArea > 0 && interArea.toFloat() / innerArea >= 0.85f &&
            outer.width.toLong() * outer.height > innerArea
    }

    /**
     * OCR satırlarını bölgelere dağıtır: merkezini içeren bölge, yoksa merkezi
     * en yakın bölge. Boş gruplar elenir; okuma sırasına göre döner.
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

    /** Maskeyi 4-komşulukla [rounds] tur aşındırır (görüntü kenarı boş sayılır). */
    private fun erode(mask: BooleanArray, width: Int, height: Int, rounds: Int) {
        var current = mask
        var scratch = BooleanArray(mask.size)
        repeat(rounds) {
            for (yy in 0 until height) {
                val row = yy * width
                for (xx in 0 until width) {
                    val i = row + xx
                    scratch[i] = current[i] &&
                        xx > 0 && current[i - 1] &&
                        xx < width - 1 && current[i + 1] &&
                        yy > 0 && current[i - width] &&
                        yy < height - 1 && current[i + width]
                }
            }
            val tmp = current
            current = scratch
            scratch = tmp
        }
        if (current !== mask) current.copyInto(mask)
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun mad(values: List<Int>, med: Int): Int =
        median(values.map { abs(it - med) })
}
