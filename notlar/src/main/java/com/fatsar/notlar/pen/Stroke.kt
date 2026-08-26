package com.fatsar.notlar.pen

/** Kalem uçları. Silgi, S Pen'in yan tuşu ya da ucu ters çevrilebilen kalemlerle de seçilir. */
enum class PenTool { PEN, HIGHLIGHTER, ERASER }

/**
 * Çizgideki tek nokta. [pressure] kalem basıncı (0..1+; parmak/faresiz
 * girişte 1.0), [tilt] kalemin eğimi (radyan) — ikisi de çizgi kalınlığını
 * belirler, böylece S Pen ile bastırınca kalın, hafifçe dokununca ince yazar.
 */
data class PenPoint(val x: Float, val y: Float, val pressure: Float = 1f, val tilt: Float = 0f)

data class Stroke(
    val tool: PenTool,
    val color: Int,
    val width: Float,
    val points: MutableList<PenPoint> = ArrayList()
) {
    /** Silgi vuruşunun bir çizgiye değip değmediğini ölçmek için kaba kutu testi. */
    fun bounds(): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        if (points.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f)
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    fun hitsPoint(x: Float, y: Float, radius: Float): Boolean {
        val b = bounds()
        if (x < b[0] - radius || x > b[2] + radius || y < b[1] - radius || y > b[3] + radius) return false
        val r2 = radius * radius
        for (i in points.indices) {
            val p = points[i]
            val dx = p.x - x
            val dy = p.y - y
            if (dx * dx + dy * dy <= r2) return true
            if (i > 0 && segmentHit(points[i - 1], p, x, y, r2)) return true
        }
        return false
    }

    private fun segmentHit(a: PenPoint, b: PenPoint, x: Float, y: Float, r2: Float): Boolean {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val len2 = vx * vx + vy * vy
        if (len2 == 0f) return false
        var t = ((x - a.x) * vx + (y - a.y) * vy) / len2
        t = t.coerceIn(0f, 1f)
        val dx = a.x + t * vx - x
        val dy = a.y + t * vy - y
        return dx * dx + dy * dy <= r2
    }
}
