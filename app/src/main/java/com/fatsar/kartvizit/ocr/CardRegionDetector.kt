package com.fatsar.kartvizit.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * Fotoğrafı küçültülmüş çözünürlükte çözerek [CardRegionFinder] ile kart
 * bölgelerini bulur ve bunları OCR koordinat uzayına ölçekler. Kart-zemin
 * kontrastı yetersizse (bölge bulunamazsa) boş liste döner; çağıran taraf
 * metin-kutusu tabanlı ayırmaya (CardSegmenter) geri düşer.
 */
object CardRegionDetector {

    private const val MAX_DIMENSION = 1200

    /**
     * @param ocrWidth/ocrHeight OCR'ın kullandığı (EXIF yönü uygulanmış) boyutlar
     * @return OCR koordinatlarına ölçeklenmiş kart bölgeleri
     */
    fun detect(context: Context, uri: Uri, ocrWidth: Int, ocrHeight: Int): List<CardRegionFinder.Region> {
        if (ocrWidth <= 0 || ocrHeight <= 0) return emptyList()
        val bitmap = decodeUpright(context, uri) ?: return emptyList()
        try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 2 || h <= 2) return emptyList()

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            val scaleX = ocrWidth.toFloat() / w
            val scaleY = ocrHeight.toFloat() / h
            return CardRegionFinder.findCards(pixels, w, h).map { r ->
                CardRegionFinder.Region(
                    (r.left * scaleX).toInt(),
                    (r.top * scaleY).toInt(),
                    (r.right * scaleX).toInt(),
                    (r.bottom * scaleY).toInt()
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Görseli küçültülmüş ve EXIF yönü uygulanmış (dik) olarak çözer. */
    private fun decodeUpright(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }
}
