package com.fatsar.notlar.data

import com.fatsar.notlar.pen.PenPoint
import com.fatsar.notlar.pen.PenTool
import com.fatsar.notlar.pen.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeSerializerTest {

    @Test
    fun `cizgiler basinc ve egimle birlikte saklanir`() {
        val stroke = Stroke(
            tool = PenTool.HIGHLIGHTER,
            color = -65536,
            width = 6f,
            points = arrayListOf(
                PenPoint(1f, 2f, 0.5f, 0.25f),
                PenPoint(3.5f, 4.25f, 1f, 0f)
            )
        )
        val json = StrokeSerializer.toJson(listOf(stroke), 1080, 1920)
        val sketch = StrokeSerializer.fromJson(json)

        assertEquals(1080, sketch.width)
        assertEquals(1920, sketch.height)
        val restored = sketch.strokes.single()
        assertEquals(PenTool.HIGHLIGHTER, restored.tool)
        assertEquals(-65536, restored.color)
        assertEquals(6f, restored.width, 0.001f)
        assertEquals(2, restored.points.size)
        assertEquals(3.5f, restored.points[1].x, 0.01f)
        assertEquals(0.5f, restored.points[0].pressure, 0.01f)
        assertEquals(0.25f, restored.points[0].tilt, 0.01f)
    }

    @Test
    fun `bozuk veri bos cizime donusur`() {
        assertTrue(StrokeSerializer.fromJson("bozuk").strokes.isEmpty())
        assertTrue(StrokeSerializer.fromJson("").strokes.isEmpty())
    }

    @Test
    fun `bilinmeyen ucta kalem varsayilir`() {
        val json = """{"version":1,"width":10,"height":10,"strokes":[{"tool":"UZAY_KALEMI","color":1,"width":2,"pts":[0,0,1,0, 5,5,1,0]}]}"""
        assertEquals(PenTool.PEN, StrokeSerializer.fromJson(json).strokes.single().tool)
    }
}
