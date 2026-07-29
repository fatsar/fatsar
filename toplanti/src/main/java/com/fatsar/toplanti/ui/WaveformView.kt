package com.fatsar.toplanti.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.fatsar.toplanti.R
import kotlin.math.max

/**
 * Canlı ses seviyesini kayan çubuklar halinde çizer. Kayıt sırasında
 * [push] ile beslenir; duraklatıldığında son görüntü korunur.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barWidthPx = dp(4f)
    private val gapPx = dp(3f)
    private val levels = ArrayDeque<Float>()
    private var capacity = 48

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_600)
    }
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_600)
        alpha = 46
    }
    private val rect = RectF()

    /** @param level 0..100 arası anlık ses seviyesi */
    fun push(level: Int) {
        val v = (level.coerceIn(0, 100) / 100f)
        levels.addLast(v)
        while (levels.size > capacity) levels.removeFirst()
        invalidate()
    }

    fun reset() {
        levels.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        capacity = max(8, (w / (barWidthPx + gapPx)).toInt())
        while (levels.size > capacity) levels.removeFirst()
    }

    override fun onDraw(canvas: Canvas) {
        val centerY = height / 2f
        val maxHalf = height / 2f - dp(2f)
        val minHalf = barWidthPx / 2f
        // Sağdan sola akış: en yeni çubuk sağda
        var x = width - barWidthPx
        for (i in levels.indices.reversed()) {
            if (x < -barWidthPx) break
            val half = (minHalf + levels.elementAt(i) * (maxHalf - minHalf))
            rect.set(x, centerY - half, x + barWidthPx, centerY + half)
            canvas.drawRoundRect(rect, barWidthPx / 2f, barWidthPx / 2f, activePaint)
            x -= (barWidthPx + gapPx)
        }
        // Henüz veri gelmemiş kısım için soluk taban çizgisi
        while (x >= -barWidthPx) {
            rect.set(x, centerY - minHalf, x + barWidthPx, centerY + minHalf)
            canvas.drawRoundRect(rect, barWidthPx / 2f, barWidthPx / 2f, idlePaint)
            x -= (barWidthPx + gapPx)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
