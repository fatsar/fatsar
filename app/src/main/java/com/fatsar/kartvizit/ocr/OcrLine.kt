package com.fatsar.kartvizit.ocr

/**
 * OCR sonucundaki tek bir metin satırı.
 * [height] satırın piksel yüksekliğidir; yazı boyutu tahmini için kullanılır
 * (kartvizitlerde en büyük yazı genellikle isim veya firma adıdır).
 */
data class OcrLine(val text: String, val height: Float = 0f)
