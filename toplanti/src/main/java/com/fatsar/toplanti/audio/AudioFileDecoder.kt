package com.fatsar.toplanti.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException

/**
 * Ses/video dosyasını çözerek 16 kHz mono PCM16 akışı üretir (FR-006).
 * WAV PCM dosyaları doğrudan okunur; diğer biçimler MediaCodec ile çözülür.
 */
class AudioFileDecoder(private val file: File) {

    /**
     * @param onPcm her PCM parçası için çağrılır; false dönerse çözme erken durur
     *              (ör. dil algılama için yalnızca baş kısım gerekir)
     * @param onProgress 0..100 arası ilerleme
     */
    @Throws(IOException::class)
    fun decode(onPcm: (ByteArray) -> Boolean, onProgress: (Int) -> Unit) {
        if (isPcmWav()) decodeWav(onPcm, onProgress) else decodeWithCodec(onPcm, onProgress)
    }

    private fun isPcmWav(): Boolean {
        if (!file.name.lowercase().endsWith(".wav")) return false
        file.inputStream().use { ins ->
            val head = ByteArray(22)
            if (ins.read(head) < 22) return false
            return String(head, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(head, 8, 4, Charsets.US_ASCII) == "WAVE" &&
                head[20].toInt() == 1 // PCM
        }
    }

    private fun decodeWav(onPcm: (ByteArray) -> Boolean, onProgress: (Int) -> Unit) {
        file.inputStream().use { ins ->
            val riff = ByteArray(12)
            require(ins.read(riff) == 12) { "Geçersiz WAV" }
            var sampleRate = 16000
            var channels = 1
            var dataSize = -1L
            // Bölümleri tara: fmt ve data
            val chunkHead = ByteArray(8)
            while (ins.read(chunkHead) == 8) {
                val id = String(chunkHead, 0, 4, Charsets.US_ASCII)
                val size = leInt(chunkHead, 4).toLong() and 0xffffffffL
                if (id == "fmt ") {
                    val fmt = ByteArray(size.toInt())
                    ins.read(fmt)
                    channels = leShort(fmt, 2)
                    sampleRate = leInt(fmt, 4)
                } else if (id == "data") {
                    dataSize = size
                    break
                } else {
                    ins.skip(size)
                }
            }
            require(dataSize >= 0) { "WAV data bölümü bulunamadı" }
            val resampler = MonoResampler(sampleRate, channels)
            val buf = ByteArray(32 * 1024)
            var read = 0L
            // dataSize 0 olabilir (kesinti sonrası başlık güncellenmemiş): akışı sonuna kadar oku
            val total = if (dataSize > 0) dataSize else file.length() - 44
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                if (!onPcm(resampler.process(buf, n))) return
                read += n
                if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
            }
            onProgress(100)
        }
    }

    private fun decodeWithCodec(onPcm: (ByteArray) -> Boolean, onProgress: (Int) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw IOException("Dosyada ses izi bulunamadı")
        }
        extractor.selectTrack(trackIndex)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else -1L

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var resampler: MonoResampler? = null
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            if (durationUs > 0) {
                                onProgress(((extractor.sampleTime * 100) / durationUs).toInt().coerceIn(0, 99))
                            }
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk)
                        if (resampler == null) {
                            val of = codec.outputFormat
                            resampler = MonoResampler(
                                of.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            )
                        }
                        if (!onPcm(resampler!!.process(chunk, chunk.size))) {
                            codec.releaseOutputBuffer(outIndex, false)
                            return
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            onProgress(100)
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
    }

    private fun leShort(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)
}
