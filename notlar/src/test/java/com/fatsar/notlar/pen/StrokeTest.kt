package com.fatsar.notlar.pen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeTest {

    private fun line(vararg coords: Float): Stroke {
        val points = ArrayList<PenPoint>()
        var i = 0
        while (i + 1 < coords.size) {
            points.add(PenPoint(coords[i], coords[i + 1]))
            i += 2
        }
        return Stroke(PenTool.PEN, 0, 4f, points)
    }

    @Test
    fun `silgi cizgiye degdiginde bulur`() {
        val stroke = line(0f, 0f, 100f, 0f)
        assertTrue(stroke.hitsPoint(50f, 3f, 8f))
        assertTrue(stroke.hitsPoint(0f, 0f, 1f))
    }

    @Test
    fun `uzaktaki dokunus cizgiyi silmez`() {
        val stroke = line(0f, 0f, 100f, 0f)
        assertFalse(stroke.hitsPoint(50f, 60f, 8f))
        assertFalse(stroke.hitsPoint(-40f, 0f, 8f))
    }

    @Test
    fun `sinir kutusu tum noktalari kapsar`() {
        val bounds = line(10f, 20f, -5f, 40f).bounds()
        assertTrue(bounds[0] == -5f && bounds[1] == 20f && bounds[2] == 10f && bounds[3] == 40f)
    }
}
