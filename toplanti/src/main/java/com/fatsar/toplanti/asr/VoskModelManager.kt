package com.fatsar.toplanti.asr

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Türkçe konuşma tanıma modelini yönetir. Model (~45 MB) yalnızca bir kez
 * indirilir ve cihazda saklanır; sonrasında tüm tanıma çevrimdışı çalışır.
 * Ses verisi hiçbir zaman cihaz dışına gönderilmez.
 */
class VoskModelManager(context: Context) {

    private val modelsRoot = File(context.filesDir, "models").apply { mkdirs() }
    private val modelDir = File(modelsRoot, MODEL_NAME)

    fun isInstalled(): Boolean = File(modelDir, "am").isDirectory || File(modelDir, "conf").isDirectory

    fun installedModelDir(): File? = if (isInstalled()) modelDir else null

    /**
     * Modeli indirir ve açar. [onProgress] 0..100 (indirme 0..80, açma 80..100).
     */
    @Throws(IOException::class)
    fun download(onProgress: (Int) -> Unit) {
        val zipFile = File(modelsRoot, "$MODEL_NAME.zip")
        try {
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                throw IOException("Model indirilemedi: HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { ins ->
                zipFile.outputStream().use { outs ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        outs.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 80) / total).toInt().coerceIn(0, 80))
                    }
                }
            }
            unzip(zipFile, onProgress)
            if (!isInstalled()) throw IOException("Model arşivi beklenen yapıda değil")
            onProgress(100)
        } finally {
            zipFile.delete()
        }
    }

    private fun unzip(zipFile: File, onProgress: (Int) -> Unit) {
        val targetRoot = modelsRoot.canonicalFile
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            var count = 0
            while (entry != null) {
                val out = File(targetRoot, entry.name)
                // Zip-slip koruması: arşiv, hedef dizin dışına yazamaz
                if (!out.canonicalPath.startsWith(targetRoot.canonicalPath + File.separator)) {
                    throw IOException("Geçersiz arşiv girdisi: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                count++
                if (count % 5 == 0) onProgress((80 + (count % 20)).coerceAtMost(99))
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        // Alpha Cephei'nin resmî küçük Türkçe modeli (Apache 2.0 lisanslı)
        const val MODEL_NAME = "vosk-model-small-tr-0.3"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SIZE_MB = 45
    }
}
