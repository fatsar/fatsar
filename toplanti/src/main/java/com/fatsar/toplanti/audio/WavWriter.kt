package com.fatsar.toplanti.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * 16 kHz / mono / 16-bit PCM veriyi standart WAV dosyasına yazar.
 * Başlıktaki boyut alanları [close] sırasında güncellenir; böylece uygulama
 * beklenmedik kapansa bile o ana kadar yazılmış veri kurtarılabilir (AC-004).
 */
class WavWriter(file: File, private val sampleRate: Int = 16000) : Closeable {

    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes: Long = 0

    init {
        raf.setLength(0)
        raf.write(buildHeader(0))
    }

    fun write(buffer: ByteArray, length: Int) {
        raf.write(buffer, 0, length)
        dataBytes += length
        // Her yazmada başlığı güncel tutmak pahalı; parça sınırlarında güncelleriz.
    }

    /** Başlığı diske işler; kesinti sonrası dosyanın oynatılabilir kalmasını sağlar. */
    fun sync() {
        val pos = raf.filePointer
        raf.seek(0)
        raf.write(buildHeader(dataBytes))
        raf.seek(pos)
    }

    override fun close() {
        sync()
        raf.close()
    }

    private fun buildHeader(data: Long): ByteArray {
        val byteRate = sampleRate * 2
        val h = ByteArray(44)
        fun putStr(off: Int, s: String) = s.toByteArray(Charsets.US_ASCII).copyInto(h, off)
        fun putIntLE(off: Int, v: Int) {
            h[off] = (v and 0xff).toByte()
            h[off + 1] = (v shr 8 and 0xff).toByte()
            h[off + 2] = (v shr 16 and 0xff).toByte()
            h[off + 3] = (v shr 24 and 0xff).toByte()
        }
        fun putShortLE(off: Int, v: Int) {
            h[off] = (v and 0xff).toByte()
            h[off + 1] = (v shr 8 and 0xff).toByte()
        }
        putStr(0, "RIFF")
        putIntLE(4, (36 + data).toInt())
        putStr(8, "WAVE")
        putStr(12, "fmt ")
        putIntLE(16, 16)          // fmt bölümü boyutu
        putShortLE(20, 1)         // PCM
        putShortLE(22, 1)         // mono
        putIntLE(24, sampleRate)
        putIntLE(28, byteRate)
        putShortLE(32, 2)         // blok hizası
        putShortLE(34, 16)        // bit derinliği
        putStr(36, "data")
        putIntLE(40, data.toInt())
        return h
    }
}
