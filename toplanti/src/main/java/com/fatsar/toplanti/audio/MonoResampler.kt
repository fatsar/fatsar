package com.fatsar.toplanti.audio

/**
 * Çok kanallı PCM16 veriyi mono'ya indirger ve doğrusal enterpolasyonla
 * hedef örnekleme hızına (16 kHz) çevirir. Parçalar arasında kesirli konum
 * taşınır; böylece uzun kayıtlarda kayma oluşmaz.
 */
class MonoResampler(private val srcRate: Int, private val channels: Int, private val dstRate: Int = 16000) {

    private var pos = 0.0          // kaynak örnek indeksinde kesirli konum
    private var lastSample = 0     // önceki parçanın son örneği (enterpolasyon için)
    private var hasLast = false

    /** [pcm] little-endian PCM16; [length] bayt sayısı. Dönen dizi mono 16 kHz PCM16. */
    fun process(pcm: ByteArray, length: Int): ByteArray {
        val frames = length / (2 * channels)
        if (frames == 0) return ByteArray(0)

        // Kanalları ortalama alarak mono'ya indir
        val mono = IntArray(frames)
        var idx = 0
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                val lo = pcm[idx].toInt() and 0xff
                val hi = pcm[idx + 1].toInt()
                sum += (hi shl 8) or lo
                idx += 2
            }
            mono[f] = sum / channels
        }

        if (srcRate == dstRate) {
            val out = ByteArray(frames * 2)
            for (f in 0 until frames) putSample(out, f, mono[f])
            return out
        }

        val step = srcRate.toDouble() / dstRate
        val outSamples = ArrayList<Int>(((frames / step) + 2).toInt())
        // pos, "önceki parçaların sonundan itibaren" kaynak indeksidir; önceki son örnek -1 konumundadır
        while (pos < frames) {
            val i = pos.toInt()
            val frac = pos - i
            val s0 = if (i == 0 && hasLast && pos < 1.0 && frac > 0) lastSample else mono[i]
            val s1 = if (i + 1 < frames) mono[i + 1] else mono[i]
            outSamples.add(((s0 + (s1 - s0) * frac)).toInt())
            pos += step
        }
        pos -= frames
        lastSample = mono[frames - 1]
        hasLast = true

        val out = ByteArray(outSamples.size * 2)
        for (f in outSamples.indices) putSample(out, f, outSamples[f])
        return out
    }

    private fun putSample(out: ByteArray, frame: Int, value: Int) {
        val v = value.coerceIn(-32768, 32767)
        out[frame * 2] = (v and 0xff).toByte()
        out[frame * 2 + 1] = (v shr 8 and 0xff).toByte()
    }
}
