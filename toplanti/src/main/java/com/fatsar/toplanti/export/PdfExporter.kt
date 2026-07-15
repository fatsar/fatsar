package com.fatsar.toplanti.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** Bölüm listesinden basit, sayfalanmış A4 PDF üretir (FR-064). */
object PdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val LINE_GAP = 4f

    fun write(out: OutputStream, sections: List<Pair<String?, String>>) {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = MARGIN
        }

        for ((style, text) in sections) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = when (style) {
                    "h1" -> 17f
                    "h2" -> 13f
                    else -> 10.5f
                }
                typeface = if (style != null) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else Typeface.DEFAULT
            }
            if (style == "h2") y += 8f
            val maxWidth = PAGE_W - 2 * MARGIN
            for (line in wrap(text, paint, maxWidth)) {
                val lineH = paint.textSize + LINE_GAP
                if (y + lineH > PAGE_H - MARGIN) newPage()
                y += lineH
                canvas.drawText(line, MARGIN, y, paint)
            }
            y += 2f
        }
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
