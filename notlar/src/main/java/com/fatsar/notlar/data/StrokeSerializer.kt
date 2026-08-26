package com.fatsar.notlar.data

import com.fatsar.notlar.pen.PenPoint
import com.fatsar.notlar.pen.PenTool
import com.fatsar.notlar.pen.Stroke
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kalem çizgileri vektör olarak saklanır (PNG'ye ek olarak): böylece çizim
 * sonradan yeniden açılıp geri alınabilir, ekran boyutu değişse de bozulmaz.
 */
object StrokeSerializer {

    const val VERSION = 1

    fun toJson(strokes: List<Stroke>, canvasWidth: Int, canvasHeight: Int): String {
        val array = JSONArray()
        for (stroke in strokes) {
            val obj = JSONObject()
            obj.put("tool", stroke.tool.name)
            obj.put("color", stroke.color)
            obj.put("width", stroke.width.toDouble())
            val pts = JSONArray()
            for (p in stroke.points) {
                pts.put(round(p.x))
                pts.put(round(p.y))
                pts.put(round(p.pressure))
                pts.put(round(p.tilt))
            }
            obj.put("pts", pts)
            array.put(obj)
        }
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("width", canvasWidth)
        root.put("height", canvasHeight)
        root.put("strokes", array)
        return root.toString()
    }

    data class Sketch(val width: Int, val height: Int, val strokes: List<Stroke>)

    fun fromJson(json: String): Sketch {
        if (json.isBlank()) return Sketch(0, 0, emptyList())
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return Sketch(0, 0, emptyList())
        }
        val array = root.optJSONArray("strokes") ?: JSONArray()
        val strokes = ArrayList<Stroke>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val tool = try {
                PenTool.valueOf(obj.optString("tool", PenTool.PEN.name))
            } catch (e: IllegalArgumentException) {
                PenTool.PEN
            }
            val pts = obj.optJSONArray("pts") ?: continue
            val points = ArrayList<PenPoint>(pts.length() / 4)
            var k = 0
            while (k + 3 < pts.length()) {
                points.add(
                    PenPoint(
                        pts.optDouble(k).toFloat(),
                        pts.optDouble(k + 1).toFloat(),
                        pts.optDouble(k + 2, 1.0).toFloat(),
                        pts.optDouble(k + 3, 0.0).toFloat()
                    )
                )
                k += 4
            }
            if (points.isEmpty()) continue
            strokes.add(
                Stroke(
                    tool = tool,
                    color = obj.optInt("color", 0xFF000000.toInt()),
                    width = obj.optDouble("width", 4.0).toFloat(),
                    points = points
                )
            )
        }
        return Sketch(root.optInt("width", 0), root.optInt("height", 0), strokes)
    }

    private fun round(value: Float): Double = Math.round(value * 100.0) / 100.0
}
