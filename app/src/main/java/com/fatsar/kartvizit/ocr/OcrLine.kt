package com.fatsar.kartvizit.ocr

/**
 * OCR sonucundaki tek bir metin satırı ve görüntüdeki konumu (piksel).
 * [height] satırın yüksekliğidir (yazı boyutu tahmini için). [left]/[top]/
 * [right]/[bottom] sınırlayıcı kutudur; tek fotoğraftaki birden fazla
 * kartvizitin konuma göre ayrılmasında kullanılır.
 */
data class OcrLine(
    val text: String,
    val height: Float = 0f,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
