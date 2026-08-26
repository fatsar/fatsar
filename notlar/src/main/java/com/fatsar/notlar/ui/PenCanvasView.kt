package com.fatsar.notlar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.fatsar.notlar.pen.PenPoint
import com.fatsar.notlar.pen.PenTool
import com.fatsar.notlar.pen.Stroke

/**
 * Kalem (S Pen ve diğer stylus'lar) için çizim/el yazısı yüzeyi.
 *
 * - **Basınç ve eğim duyarlı**: bastırınca kalınlaşır, hafif dokunuşta incelir.
 * - **Avuç içi reddi**: kalem bir kez algılandıktan sonra parmak dokunuşları
 *   çizgi üretmez (yazarken elin ekrana dayanması sorun olmaz).
 * - **Yan tuş = silgi**: S Pen'in yan düğmesi basılıyken ya da ucu ters çevrilen
 *   kalemlerde (TOOL_TYPE_ERASER) silgi devreye girer.
 * - **Havada gezinme (hover)**: kalem ekrana değmeden yaklaştığında ucun
 *   nereye geleceğini gösteren halka çizilir.
 * - Ara noktalar (`getHistorical*`) kullanılır; hızlı yazarken çizgi kırık olmaz.
 */
class PenCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    var tool: PenTool = PenTool.PEN
    var penColor: Int = Color.BLACK
    /** Kalem ucu kalınlığı (dp). */
    var penWidthDp: Float = 3f

    /** Kalem görüldükten sonra parmak dokunuşları yok sayılsın mı? */
    var stylusOnly: Boolean = true

    var onStrokesChanged: (() -> Unit)? = null
    var onStylusDetected: (() -> Unit)? = null

    private val strokes = ArrayList<Stroke>()
    private val undone = ArrayList<Stroke>()
    private var active: Stroke? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var stylusSeen = false

    private var hovering = false
    private var hoverX = 0f
    private var hoverY = 0f

    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val path = Path()

    val strokeCount: Int get() = strokes.size
    val isEmpty: Boolean get() = strokes.isEmpty()
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    fun strokesSnapshot(): List<Stroke> = strokes.toList()

    /** Kayıtlı çizimi yükler; farklı ekran boyutunda kaydedilmişse ölçekler. */
    fun load(loaded: List<Stroke>, sourceWidth: Int, sourceHeight: Int) {
        strokes.clear()
        undone.clear()
        val scale = if (sourceWidth > 0 && width > 0) width.toFloat() / sourceWidth else 1f
        for (stroke in loaded) {
            val scaled = if (scale == 1f) stroke else Stroke(
                tool = stroke.tool,
                color = stroke.color,
                width = stroke.width * scale,
                points = stroke.points.mapTo(ArrayList()) {
                    PenPoint(it.x * scale, it.y * scale, it.pressure, it.tilt)
                }
            )
            strokes.add(scaled)
        }
        redrawCache()
        invalidate()
        onStrokesChanged?.invoke()
    }

    fun undo() {
        if (strokes.isEmpty()) return
        undone.add(strokes.removeAt(strokes.size - 1))
        redrawCache()
        invalidate()
        onStrokesChanged?.invoke()
    }

    fun redo() {
        if (undone.isEmpty()) return
        strokes.add(undone.removeAt(undone.size - 1))
        redrawCache()
        invalidate()
        onStrokesChanged?.invoke()
    }

    fun clear() {
        if (strokes.isEmpty()) return
        undone.addAll(strokes.asReversed())
        strokes.clear()
        redrawCache()
        invalidate()
        onStrokesChanged?.invoke()
    }

    /** Çizimi PNG olarak dışa aktarmak için saydam zeminli görüntü üretir. */
    fun exportBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (stroke in strokes) drawStroke(canvas, stroke)
        return bitmap
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cache = if (w > 0 && h > 0) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) else null
        cacheCanvas = cache?.let { Canvas(it) }
        redrawCache()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        active?.let { drawStroke(canvas, it) }
        if (hovering) {
            hoverPaint.color = if (tool == PenTool.ERASER) Color.GRAY else penColor
            hoverPaint.alpha = 140
            canvas.drawCircle(hoverX, hoverY, hoverRadius(), hoverPaint)
        }
    }

    private fun hoverRadius(): Float = when (tool) {
        PenTool.ERASER -> eraserRadius()
        PenTool.HIGHLIGHTER -> penWidthDp * density * 2f
        PenTool.PEN -> (penWidthDp * density).coerceAtLeast(3f)
    }

    private fun eraserRadius(): Float = (penWidthDp * density * 3f).coerceAtLeast(12f * density)

    // --- Giriş ---

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hovering = isStylus(event.getToolType(0))
                hoverX = event.x
                hoverY = event.y
                invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hovering = false
                invalidate()
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) event.actionIndex else 0
        val toolType = event.getToolType(index)
        val stylus = isStylus(toolType)
        if (stylus && !stylusSeen) {
            stylusSeen = true
            onStylusDetected?.invoke()
        }
        // Avuç içi / parmak reddi: kalem kullanılan bir oturumda parmak çizmez.
        if (!stylus && stylusOnly && stylusSeen) return false

        val erasing = toolType == MotionEvent.TOOL_TYPE_ERASER ||
            tool == PenTool.ERASER ||
            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                hovering = false
                if (erasing) eraseAt(event.x, event.y) else beginStroke(event, 0)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                if (erasing) {
                    for (h in 0 until event.historySize) {
                        eraseAt(event.getHistoricalX(pointerIndex, h), event.getHistoricalY(pointerIndex, h))
                    }
                    eraseAt(event.getX(pointerIndex), event.getY(pointerIndex))
                } else {
                    val stroke = active ?: beginStroke(event, pointerIndex)
                    for (h in 0 until event.historySize) {
                        stroke.points.add(historicalPoint(event, pointerIndex, h))
                    }
                    stroke.points.add(currentPoint(event, pointerIndex))
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishStroke()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                invalidate()
                return true
            }
        }
        return true
    }

    private fun isStylus(toolType: Int): Boolean =
        toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

    private fun beginStroke(event: MotionEvent, pointerIndex: Int): Stroke {
        val stroke = Stroke(
            tool = if (tool == PenTool.ERASER) PenTool.PEN else tool,
            color = penColor,
            width = penWidthDp * density
        )
        stroke.points.add(currentPoint(event, pointerIndex))
        active = stroke
        undone.clear()
        return stroke
    }

    private fun finishStroke() {
        val stroke = active ?: return
        active = null
        if (stroke.points.size < 2) {
            // Tek dokunuş: minik bir nokta bırak
            val p = stroke.points.firstOrNull() ?: return
            stroke.points.add(PenPoint(p.x + 0.6f, p.y + 0.6f, p.pressure, p.tilt))
        }
        strokes.add(stroke)
        cacheCanvas?.let { drawStroke(it, stroke) }
        onStrokesChanged?.invoke()
    }

    private fun eraseAt(x: Float, y: Float) {
        val radius = eraserRadius()
        val before = strokes.size
        strokes.removeAll { it.hitsPoint(x, y, radius) }
        if (strokes.size != before) {
            undone.clear()
            redrawCache()
            onStrokesChanged?.invoke()
        }
    }

    private fun currentPoint(event: MotionEvent, pointerIndex: Int) = PenPoint(
        event.getX(pointerIndex),
        event.getY(pointerIndex),
        event.getPressure(pointerIndex),
        event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
    )

    private fun historicalPoint(event: MotionEvent, pointerIndex: Int, position: Int) = PenPoint(
        event.getHistoricalX(pointerIndex, position),
        event.getHistoricalY(pointerIndex, position),
        event.getHistoricalPressure(pointerIndex, position),
        event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, position)
    )

    private fun redrawCache() {
        val canvas = cacheCanvas ?: return
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        for (stroke in strokes) drawStroke(canvas, stroke)
    }

    /**
     * Çizgi, her parçası kendi kalınlığıyla çizilen kısa eğrilerden oluşur;
     * kalınlık basınç ve eğime göre değişir. Fosforlu kalem yarı saydam ve
     * daha kalın çizer, kalemin altındaki yazı okunmaya devam eder.
     */
    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        val points = stroke.points
        if (points.isEmpty()) return
        paint.color = stroke.color
        paint.alpha = if (stroke.tool == PenTool.HIGHLIGHTER) 90 else 255
        val widthFactor = if (stroke.tool == PenTool.HIGHLIGHTER) 4.5f else 1f

        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[0].x, points[0].y, stroke.width * 0.5f, paint)
            paint.style = Paint.Style.STROKE
            return
        }

        var previousMidX = points[0].x
        var previousMidY = points[0].y
        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val current = points[i]
            val midX = (previous.x + current.x) / 2f
            val midY = (previous.y + current.y) / 2f
            path.reset()
            path.moveTo(previousMidX, previousMidY)
            path.quadTo(previous.x, previous.y, midX, midY)
            paint.strokeWidth = widthFor(stroke, current) * widthFactor
            canvas.drawPath(path, paint)
            previousMidX = midX
            previousMidY = midY
        }
    }

    private fun widthFor(stroke: Stroke, point: PenPoint): Float {
        val pressure = point.pressure.coerceIn(0f, 2f)
        // Basınç bildirmeyen girişlerde (parmak, fare) 1.0 gelir → sabit kalınlık.
        val factor = 0.45f + 0.85f * pressure
        val tiltBoost = 1f + 0.25f * (point.tilt / 1.5f).coerceIn(0f, 1f)
        return (stroke.width * factor * tiltBoost).coerceAtLeast(0.8f)
    }
}
