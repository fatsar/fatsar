package com.fatsar.kartvizit.ocr

import kotlin.math.max

/**
 * Tek bir fotoğraftaki OCR satırlarını konumlarına göre kümeleyerek birden
 * fazla kartviziti ayırır. İki kart yan yana ya da alt alta konduğunda
 * aralarındaki boşluk ("oluk") satır kutularının kapsamında bir boşluk
 * olarak görünür; bu boşluktan bölünür.
 *
 * Aşırı bölünmeyi önlemek için muhafazakârdır: yalnızca tüm parçalar
 * bağımsız bir kartvizit gibi görünüyorsa (yeterli metin, e-posta ya da
 * telefon) çoklu karta ayırır; aksi halde tek kart döndürür.
 */
object CardSegmenter {

    private const val MAX_DEPTH = 3
    private val PHONE_DIGITS = Regex("""\d""")

    /**
     * @param imageWidth/imageHeight görüntü boyutu (piksel). 0 verilirse
     * satır kutularının kapsamından tahmin edilir.
     */
    fun segment(lines: List<OcrLine>, imageWidth: Int = 0, imageHeight: Int = 0): List<List<OcrLine>> {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.size < 6 || !hasBoxes(usable)) return listOf(usable).filter { it.isNotEmpty() }

        val w = if (imageWidth > 0) imageWidth else usable.maxOf { it.right } - usable.minOf { it.left }
        val h = if (imageHeight > 0) imageHeight else usable.maxOf { it.bottom } - usable.minOf { it.top }

        val clusters = partition(usable, w, h, 0)
        return if (clusters.size > 1 && clusters.all { looksLikeCard(it) }) {
            // Kartları görüntüdeki konumlarına göre sırala: üstten alta, soldan sağa
            clusters.sortedWith(compareBy({ it.minOf { l -> l.top } }, { it.minOf { l -> l.left } }))
        } else {
            listOf(usable)
        }
    }

    private fun partition(lines: List<OcrLine>, w: Int, h: Int, depth: Int): List<List<OcrLine>> {
        if (depth >= MAX_DEPTH || lines.size < 4) return listOf(lines)

        val medianLineHeight = lines.map { max(1, it.bottom - it.top) }.sorted()
            .let { it[it.size / 2] }

        val cut = bestGutter(lines, w, h, medianLineHeight) ?: return listOf(lines)
        val (a, b) = split(lines, cut)
        if (a.isEmpty() || b.isEmpty()) return listOf(lines)

        return partition(a, w, h, depth + 1) + partition(b, w, h, depth + 1)
    }

    private data class Gutter(val axis: Axis, val position: Int, val size: Int)
    private enum class Axis { X, Y }

    /** İki eksende de en büyük geçerli oluğu arar; yoksa null. */
    private fun bestGutter(lines: List<OcrLine>, w: Int, h: Int, lineHeight: Int): Gutter? {
        val candidates = listOfNotNull(
            largestGap(lines, Axis.X) { it.left to it.right }
                ?.takeIf { qualifies(it.size, w, lineHeight) },
            largestGap(lines, Axis.Y) { it.top to it.bottom }
                ?.takeIf { qualifies(it.size, h, lineHeight) }
        )
        return candidates.maxByOrNull { it.size }
    }

    /** Bir gap, görüntünün %6'sından ve satır yüksekliğinin ~2 katından büyükse geçerli. */
    private fun qualifies(gapSize: Int, dimension: Int, lineHeight: Int): Boolean =
        gapSize >= max((dimension * 0.06).toInt(), (lineHeight * 2.0).toInt())

    /**
     * Verilen eksende satır kutularının birleşik kapsamındaki en büyük boşluğu
     * bulur (oluk). Kutuları [start,end] aralıklarına indirger, birleştirir ve
     * ardışık kapalı bölümler arasındaki boşlukları ölçer.
     */
    private inline fun largestGap(
        lines: List<OcrLine>,
        axis: Axis,
        selector: (OcrLine) -> Pair<Int, Int>
    ): Gutter? {
        val intervals = lines.map(selector).sortedBy { it.first }
        var coveredEnd = intervals.first().second
        var best: Gutter? = null
        for ((start, end) in intervals.drop(1)) {
            if (start > coveredEnd) {
                val size = start - coveredEnd
                if (best == null || size > best!!.size) {
                    best = Gutter(axis, coveredEnd + size / 2, size)
                }
            }
            if (end > coveredEnd) coveredEnd = end
        }
        return best
    }

    private fun split(lines: List<OcrLine>, cut: Gutter): Pair<List<OcrLine>, List<OcrLine>> {
        val (a, b) = lines.partition {
            val center = if (cut.axis == Axis.X) it.centerX else it.centerY
            center < cut.position
        }
        return a to b
    }

    private fun hasBoxes(lines: List<OcrLine>): Boolean =
        lines.any { it.right > it.left && it.bottom > it.top }

    /** Bir küme gerçek bir kartvizit gibi mi? En az 3 satır ya da e-posta/telefon. */
    private fun looksLikeCard(lines: List<OcrLine>): Boolean {
        if (lines.size >= 3) return true
        val text = lines.joinToString(" ") { it.text }
        if (text.contains('@')) return true
        return PHONE_DIGITS.findAll(text).count() >= 7
    }
}
