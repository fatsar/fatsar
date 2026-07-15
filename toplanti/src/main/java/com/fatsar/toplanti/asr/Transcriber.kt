package com.fatsar.toplanti.asr

import com.fatsar.toplanti.model.TranscriptSegment
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable
import java.io.File

/**
 * Vosk ile 16 kHz mono PCM16 akışını zaman damgalı, güven skorlu transkript
 * segmentlerine dönüştürür (FR-010, FR-015). Tamamen cihaz üzerinde çalışır.
 */
class Transcriber(modelDir: File) : Closeable {

    private val model = Model(modelDir.absolutePath)
    private val recognizer = Recognizer(model, SAMPLE_RATE).apply { setWords(true) }
    private val segments = mutableListOf<TranscriptSegment>()

    private data class Word(val text: String, val startMs: Long, val endMs: Long, val conf: Double)

    /** PCM parçasını işler; tamamlanan cümleler segment listesine eklenir. */
    fun accept(pcm: ByteArray, length: Int) {
        if (length <= 0) return
        if (recognizer.acceptWaveForm(pcm, length)) {
            parseResult(recognizer.result)
        }
    }

    /** Akışı sonlandırır ve tüm segmentleri döndürür. */
    fun finish(): List<TranscriptSegment> {
        parseResult(recognizer.finalResult)
        return segments.toList()
    }

    override fun close() {
        recognizer.close()
        model.close()
    }

    private fun parseResult(json: String) {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val arr = obj.optJSONArray("result") ?: return
        if (arr.length() == 0) return
        val words = (0 until arr.length()).map { i ->
            val w = arr.getJSONObject(i)
            Word(
                text = w.getString("word"),
                startMs = (w.getDouble("start") * 1000).toLong(),
                endMs = (w.getDouble("end") * 1000).toLong(),
                conf = w.optDouble("conf", 1.0)
            )
        }
        // Uzun sözceleri sessizlik boşluklarından ve kelime sayısından bölerek segmentle
        val current = mutableListOf<Word>()
        for (w in words) {
            if (current.isNotEmpty() &&
                (w.startMs - current.last().endMs > GAP_SPLIT_MS || current.size >= MAX_WORDS)
            ) {
                emit(current)
                current.clear()
            }
            current.add(w)
        }
        if (current.isNotEmpty()) emit(current)
    }

    private fun emit(words: List<Word>) {
        val text = words.joinToString(" ") { it.text }
        if (text.isBlank()) return
        segments.add(
            TranscriptSegment(
                startMs = words.first().startMs,
                endMs = words.last().endMs,
                rawText = text,
                confidence = words.map { it.conf }.average()
            )
        )
    }

    companion object {
        const val SAMPLE_RATE = 16000.0f
        private const val GAP_SPLIT_MS = 900L
        private const val MAX_WORDS = 42
    }
}
