package com.fatsar.kartvizit.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max

/**
 * Fotoğrafı **tek kez** dik (EXIF uygulanmış) bir bitmap olarak çözer; aynı
 * bitmap hem OCR'a hem de kart bölgesi tespitine verilir. Böylece görüntünün
 * ikinci kez çözülmesinden/URI'nin yeniden açılmasından kaynaklanan
 * tutarsızlık ve sessiz başarısızlık ortadan kalkar.
 *
 * [decodeUpright] paylaşılan bitmap'i üretir; [groupFromBitmap] bu bitmap
 * üzerinden kart bölgelerini bulup OCR satırlarını kartlara gruplar. Kart-zemin
 * kontrastı yetersizse (bölge bulunamazsa) null döner; çağıran taraf
 * metin-kutusu tabanlı ayırmaya geri düşer.
 */
object CardRegionDetector {

    /** Paylaşılan bitmap için üst sınır (OCR kalitesi için yüksek tutulur). */
    private const val SHARED_MAX = 2600

    /** Bölge tespiti için çalışma çözünürlüğü. */
    private const val REGION_MAX = 1200

    /** Görseli EXIF yönü uygulanmış (dik), en fazla [maxDim] px olarak çözer. */
    fun decodeUpright(context: Context, uri: Uri, maxDim: Int = SHARED_MAX): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
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

    /**
     * Paylaşılan (dik) bitmap üzerinden kart bölgelerini bulup OCR satırlarını
     * gruplar. OCR satır koordinatları da bu bitmap uzayındadır.
     * @return kart başına satır grupları; bölge bulunamazsa null
     */
    fun groupFromBitmap(bitmap: Bitmap, lines: List<OcrLine>): List<List<OcrLine>>? {
        val ocrW = bitmap.width
        val ocrH = bitmap.height
        if (ocrW <= 8 || ocrH <= 8 || lines.isEmpty()) return null

        // Bölge tespiti için çalışma çözünürlüğüne indir
        var sample = 1
        while (max(ocrW, ocrH) / sample > REGION_MAX) sample *= 2
        val w = ocrW / sample
        val h = ocrH / sample

        val pixels = IntArray(w * h)
        if (sample == 1) {
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        } else {
            val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            if (small != bitmap) small.recycle()
        }

        val downRegions = CardRegionFinder.findCards(pixels, w, h)
        if (downRegions.isEmpty()) return null

        // Bölgeleri OCR (bitmap) koordinatlarına ölçekle
        val scaleX = ocrW.toFloat() / w
        val scaleY = ocrH.toFloat() / h
        val ocrRegions = downRegions.map { r ->
            CardRegionFinder.Region(
                (r.left * scaleX).toInt(),
                (r.top * scaleY).toInt(),
                (r.right * scaleX).toInt(),
                (r.bottom * scaleY).toInt()
            )
        }

        val bridge: (CardRegionFinder.Region, CardRegionFinder.Region) -> Boolean = { a, b ->
            CardRegionFinder.paperBridge(
                pixels, w, h,
                scaleRegion(a, 1f / scaleX, 1f / scaleY),
                scaleRegion(b, 1f / scaleX, 1f / scaleY)
            )
        }
        return CardRegionFinder.refine(ocrRegions, lines, bridge)
    }

    private fun scaleRegion(
        r: CardRegionFinder.Region,
        sx: Float,
        sy: Float
    ): CardRegionFinder.Region = CardRegionFinder.Region(
        (r.left * sx).toInt(),
        (r.top * sy).toInt(),
        (r.right * sx).toInt(),
        (r.bottom * sy).toInt()
    )
}
