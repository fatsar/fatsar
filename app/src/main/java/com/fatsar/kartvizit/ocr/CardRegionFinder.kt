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
     *
     * 1. Tümüyle başka bir bölgenin içinde kalan parça, kapsayana katılır.
     * 2. Yakın bölge çiftleri yalnızca [bridge] "aradaki şerit kart yüzeyi"
     *    derse birleştirilir: parlamada kart gövdesi parçalara ayrılsa bile
     *    parçalar arası şerit kâğıttır ve kart bütünlenir; komşu ayrı
     *    kartların arasında ise zemin görünür ve birleşmezler.
     * 3. Her satır, merkezini içeren bölgeye; yoksa dikdörtgenine en yakın
     *    bölgeye atanır; hiç satır düşmeyen bölgeler elenir.
     * 4. İletişimsiz tek satırlık kırıntılar en yakın gruba katılır.
     */
    fun refine(
        candidates: List<Region>,
        lines: List<OcrLine>,
        bridge: (Region, Region) -> Boolean = { _, _ -> false }
    ): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        if (candidates.isEmpty()) return listOf(lines)

        // 2) Köprü onaylı birleştirme: en yakın çiftten başla
        val merged = candidates.toMutableList()
        while (merged.size > 1) {
            var bestI = -1
            var bestJ = -1
            var bestGap = Int.MAX_VALUE
            for (i in merged.indices) {
                for (j in i + 1 until merged.size) {
                    val gap = rectGap(merged[i], merged[j])
                    if (gap >= bestGap) continue
                    val maxGap = (0.9f * min(
                        min(merged[i].width, merged[i].height),
                        min(merged[j].width, merged[j].height)
                    )).toInt()
                    if (gap <= maxGap && bridge(merged[i], merged[j])) {
                        bestI = i; bestJ = j; bestGap = gap
                    }
                }
            }
            if (bestI < 0) break
            val b = merged.removeAt(bestJ)
            val a = merged[bestI]
            merged[bestI] = Region(
                min(a.left, b.left), min(a.top, b.top),
                max(a.right, b.right), max(a.bottom, b.bottom)
            )
        }

        // 1) İçte kalan parça bölgeleri kapsayana katmak için indeks eşlemesi
        val target = IntArray(merged.size) { it }
        for (i in merged.indices) {
            for (j in merged.indices) {
                if (i != j && contains(merged[j], merged[i])) {
                    target[i] = j
                    break
                }
            }
        }
        @Suppress("NAME_SHADOWING")
        val candidates = merged

        // 1) Satırları (kapsayıcı) bölgelere dağıt. Bölge dışına taşan satır,
        //    merkezine değil DİKDÖRTGENİNE en yakın bölgeye gider: kart
        //    kenarındaki satır, uzaktaki bir parlama bölgesine "çalınmaz".
        val buckets = List(candidates.size) { mutableListOf<OcrLine>() }
        for (line in lines) {
            val cx = line.centerX
            val cy = line.centerY
            var index = candidates.indexOfFirst { it.contains(cx, cy) }
            if (index < 0) {
                index = candidates.indices.minByOrNull { i ->
                    rectDistanceSq(candidates[i], cx, cy)
                } ?: 0
            }
            buckets[target[index]].add(line)
        }

        // 2) Metinsiz bölgeleri ele
        val groups = candidates.indices
            .filter { target[it] == it && buckets[it].isNotEmpty() }
            .map { buckets[it].toMutableList() }
            .toMutableList()

        // 3) İletişim bilgisi olmayan tek satırlık kırıntı gruplar (zemin
        //    yansımasına düşen tek satır gibi) en yakın gruba katılır
        while (groups.size > 1) {
            val crumbIndex = groups.indices.firstOrNull { i ->
                groups[i].size == 1 && groups[i].none { l ->
                    l.text.contains('@') || l.text.count { c -> c.isDigit() } >= 7
                }
            } ?: break
            val crumb = groups.removeAt(crumbIndex)
            val cx = crumb[0].centerX
            val cy = crumb[0].centerY
            val nearest = groups.minByOrNull { g ->
                g.minOf { l ->
                    val dx = (l.centerX - cx).toLong()
                    val dy = (l.centerY - cy).toLong()
                    dx * dx + dy * dy
                }
            }
            nearest?.addAll(crumb)
        }

        return groups
            .sortedWith(compareBy({ it.minOf { l -> l.top } }, { it.minOf { l -> l.left } }))
    }

    /** Bir noktanın dikdörtgene (kenarlarına) uzaklığının karesi. */
    private fun rectDistanceSq(r: Region, x: Int, y: Int): Long {
        val dx = when {
            x < r.left -> (r.left - x).toLong()
            x > r.right -> (x - r.right).toLong()
            else -> 0L
        }
        val dy = when {
            y < r.top -> (r.top - y).toLong()
            y > r.bottom -> (y - r.bottom).toLong()
            else -> 0L
        }
        return dx * dx + dy * dy
    }

    /**
     * İki bölge arasındaki şeridin kart yüzeyi olup olmadığını piksellerden
     * anlar. Bölgeler, findCards'ın eklediği dolgu payından arındırılıp
     * (içeri çekilerek) kenar bantları KARTIN GERÇEK YÜZEYİNDEN örneklenir;
     * şerit bu yüzey renklerinden birine benziyorsa kâğıt devam ediyor
     * demektir (aynı kart), benzemiyorsa arada zemin vardır (ayrı kartlar).
     */
    fun paperBridge(argb: IntArray, width: Int, height: Int, a: Region, b: Region): Boolean {
        // findCards'ın eklediği dolgu payını geri al: gerçek kart kenarları
        val pad = max(2, min(width, height) / 160) + 1
        val ua = shrinkRegion(a, pad) ?: return false
        val ub = shrinkRegion(b, pad) ?: return false

        val dx = max(0, max(ua.left, ub.left) - min(ua.right, ub.right))
        val dy = max(0, max(ua.top, ub.top) - min(ua.bottom, ub.bottom))
        // Bitişik/örtüşen: aralarında incelenecek şerit yok; iki ayrı kartın
        // köşe teması da böyle görünür -> muhafazakâr davran, birleştirme
        if (dx == 0 && dy == 0) return false

        val horizontal = dx >= dy
        val first: Region
        val second: Region
        if (horizontal) {
            if (ua.left <= ub.left) { first = ua; second = ub } else { first = ub; second = ua }
        } else {
            if (ua.top <= ub.top) { first = ua; second = ub } else { first = ub; second = ua }
        }

        // Diğer eksende örtüşmenin orta yarısı üzerinde çalış
        val overlapLo = if (horizontal) max(first.top, second.top) else max(first.left, second.left)
        val overlapHi = if (horizontal) min(first.bottom, second.bottom) else min(first.right, second.right)
        if (overlapHi - overlapLo < 4) return false
        val quarter = (overlapHi - overlapLo) / 4
        val sLo = overlapLo + quarter
        val sHi = overlapHi - quarter

        // Şerit: gerçek kenarlar arasındaki salt boşluk (kart kenarı karışmaz)
        val gapLo = if (horizontal) first.right + 1 else first.bottom + 1
        val gapHi = if (horizontal) second.left - 1 else second.top - 1
        if (gapHi < gapLo) return false

        // Kenar bantları: gerçek kenarın belirgin biçimde İÇİNDEN (kart
        // yüzeyi). Derinlik bölge boyutuyla orantılı: kart fotoğrafta hafif
        // dönükse bbox kenarı yer yer zemine düşer; derin bant bunu aşar.
        val inA = max(
            4,
            min(
                min(first.width, first.height),
                min(second.width, second.height)
            ) / 7
        )
        val band = 6
        val edgeA: IntArray?
        val edgeB: IntArray?
        val strip: List<IntArray>
        if (horizontal) {
            edgeA = colorStats(argb, width, height, first.right - inA - band, first.right - inA, sLo, sHi)
            edgeB = colorStats(argb, width, height, second.left + inA, second.left + inA + band, sLo, sHi)
            strip = colorSamples(argb, width, height, gapLo, gapHi, sLo, sHi)
        } else {
            edgeA = colorStats(argb, width, height, sLo, sHi, first.bottom - inA - band, first.bottom - inA)
            edgeB = colorStats(argb, width, height, sLo, sHi, second.top + inA, second.top + inA + band)
            strip = colorSamples(argb, width, height, sLo, sHi, gapLo, gapHi)
        }
        if (edgeA == null || edgeB == null || strip.size < 4) return false

        fun matches(p: IntArray, e: IntArray): Boolean =
            abs(p[0] - e[0]) <= 10 && abs(p[1] - e[1]) <= 10 && abs(p[2] - e[2]) <= 55

        val matching = strip.count { matches(it, edgeA) || matches(it, edgeB) }
        return matching.toFloat() / strip.size >= 0.7f
    }

    private fun shrinkRegion(r: Region, amount: Int): Region? {
        val s = Region(r.left + amount, r.top + amount, r.right - amount, r.bottom - amount)
        return if (s.right - s.left >= 12 && s.bottom - s.top >= 12) s else null
    }

    /** Dikdörtgen içinden örneklenen (cr, cg, lum) medyanları; azsa null. */
    private fun colorStats(
        argb: IntArray, width: Int, height: Int,
        xLo: Int, xHi: Int, yLo: Int, yHi: Int
    ): IntArray? {
        val samples = colorSamples(argb, width, height, xLo, xHi, yLo, yHi)
        if (samples.size < 4) return null
        return intArrayOf(
            median(samples.map { it[0] }),
            median(samples.map { it[1] }),
            median(samples.map { it[2] })
        )
    }

    /** Dikdörtgenden en çok ~16x16 örnek: her biri (cr, cg, lum). */
    private fun colorSamples(
        argb: IntArray, width: Int, height: Int,
        xLo: Int, xHi: Int, yLo: Int, yHi: Int
    ): List<IntArray> {
        if (xHi < xLo || yHi < yLo) return emptyList()
        val result = ArrayList<IntArray>()
        val stepX = max(1, (xHi - xLo + 1) / 16)
        val stepY = max(1, (yHi - yLo + 1) / 16)
        var yy = yLo
        while (yy <= yHi) {
            var xx = xLo
            while (xx <= xHi) {
                if (xx in 0 until width && yy in 0 until height) {
                    val p = argb[yy * width + xx]
                    val r = p ushr 16 and 0xFF
                    val g = p ushr 8 and 0xFF
                    val bch = p and 0xFF
                    val sum = r + g + bch + 1
                    result.add(
                        intArrayOf(
                            255 * r / sum,
                            255 * g / sum,
                            (r * 299 + g * 587 + bch * 114) / 1000
                        )
                    )
                }
                xx += stepX
            }
            yy += stepY
        }
        return result
    }

    /** İki dikdörtgen arasındaki eksensel boşluk (kesişiyorsa 0). */
    private fun rectGap(a: Region, b: Region): Int {
        val dx = max(0, max(a.left, b.left) - min(a.right, b.right))
        val dy = max(0, max(a.top, b.top) - min(a.bottom, b.bottom))
        return max(dx, dy)
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
