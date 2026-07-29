package com.fatsar.toplanti.audio

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * İçe aktarılan dosyayı işlemeden önce doğrular: ses izi var mı, süresi ne
 * kadar (FR-006, PRD 12.1 girdi doğrulama). Bozuk/desteklenmeyen dosyalar
 * kullanıcıya işleme başlamadan bildirilir.
 */
object AudioProbe {

    data class Result(val hasAudio: Boolean, val durationMs: Long)

    fun probe(file: File): Result {
        var durationMs = 0L
        var hasAudio = false
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            hasAudio =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
            // Çözümlenemedi; WAV ise başlıktan hesaplamayı dene
        } finally {
            runCatching { retriever.release() }
        }

        if (!hasAudio || durationMs <= 0L) {
            wavDurationMs(file)?.let {
                durationMs = it
                hasAudio = true
            }
        }
        return Result(hasAudio, durationMs)
    }

    /** Ham PCM WAV dosyaları bazı cihazlarda metadata döndürmez; başlıktan hesapla. */
    private fun wavDurationMs(file: File): Long? {
        if (!file.name.lowercase().endsWith(".wav") || file.length() < 44) return null
        return runCatching {
            file.inputStream().use { ins ->
                val head = ByteArray(44)
                if (ins.read(head) < 44) return null
                if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(head, 8, 4, Charsets.US_ASCII) != "WAVE"
                ) return null
                val channels = leShort(head, 22).coerceAtLeast(1)
                val sampleRate = leInt(head, 24)
                val bitsPerSample = leShort(head, 34).coerceAtLeast(8)
                if (sampleRate <= 0) return null
                val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
                if (bytesPerSecond <= 0) return null
                (file.length() - 44) * 1000L / bytesPerSecond
            }
        }.getOrNull()
    }

    private fun leShort(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)
}
