package com.fatsar.toplanti.asr

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Türkçe konuşma tanıma modelini yönetir. Model (~35 MB) yalnızca bir kez
 * indirilir ve cihazda saklanır; sonrasında tüm tanıma çevrimdışı çalışır.
 * Ses verisi hiçbir zaman cihaz dışına gönderilmez.
 *
 * İndirme sunucusu (alphacephei.com) bot koruması kullandığından istekler
 * tarayıcı benzeri User-Agent ile yapılır, yönlendirmeler elle izlenir ve
 * inen içeriğin gerçekten ZIP olduğu doğrulanır. Kullanıcı modeli tarayıcıyla
 * indirip [installFromStream] ile elle de kurabilir.
 */
class VoskModelManager(context: Context) {

    private val modelsRoot = File(context.filesDir, "models").apply { mkdirs() }
    private val modelDir = File(modelsRoot, MODEL_NAME)

    fun isInstalled(): Boolean = hasModelContent(modelDir)

    fun installedModelDir(): File? = if (isInstalled()) modelDir else null

    /** Modeli indirir ve kurar. [onProgress] 0..100 (indirme 0..80, kurulum 80..100). */
    @Throws(IOException::class)
    fun download(onProgress: (Int) -> Unit) {
        val zipFile = File(modelsRoot, "$MODEL_NAME.zip")
        try {
            fetch(MODEL_URL, zipFile, onProgress)
            installZip(zipFile, onProgress)
        } finally {
            zipFile.delete()
        }
    }

    /** Kullanıcının elle seçtiği model ZIP'ini kurar (tarayıcıyla indirme yolu). */
    @Throws(IOException::class)
    fun installFromStream(input: InputStream, onProgress: (Int) -> Unit) {
        val zipFile = File(modelsRoot, "$MODEL_NAME.zip")
        try {
            zipFile.outputStream().use { input.copyTo(it) }
            onProgress(50)
            installZip(zipFile, onProgress)
        } finally {
            zipFile.delete()
        }
    }

    // ---- İndirme ----

    private fun fetch(startUrl: String, dest: File, onProgress: (Int) -> Unit) {
        var url = URL(startUrl)
        var redirects = 0
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.5")
            }
            val code = conn.responseCode
            // Şema değişen (http→https vb.) yönlendirmeleri de elle izle
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                    ?: throw IOException("Model indirilemedi: HTTP $code (yönlendirme adresi yok)")
                conn.disconnect()
                if (++redirects > 5) throw IOException("Model indirilemedi: çok fazla yönlendirme")
                url = URL(url, loc)
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Model indirilemedi: HTTP $code")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { ins ->
                dest.outputStream().use { outs ->
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
            return
        }
    }

    // ---- Kurulum ----

    private fun installZip(zipFile: File, onProgress: (Int) -> Unit) {
        if (!looksLikeZip(zipFile)) {
            // Tipik neden: sunucunun bot koruması ZIP yerine HTML sayfası döndürdü
            throw IOException(
                "Sunucu ZIP yerine farklı bir içerik döndürdü (erişim engeli olabilir). " +
                    "Yeniden deneyin ya da modeli tarayıcınızla indirip \"ZIP seç\" ile kurun."
            )
        }
        unzip(zipFile, onProgress)
        normalizeModelDir()
        if (!isInstalled()) throw IOException("Model arşivi beklenen yapıda değil")
        onProgress(100)
    }

    private fun looksLikeZip(file: File): Boolean {
        if (file.length() < 4) return false
        file.inputStream().use { ins ->
            val sig = ByteArray(2)
            if (ins.read(sig) != 2) return false
            return sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte()
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

    /**
     * Arşivin kök klasör adı beklenenden farklıysa (ör. farklı sürüm adı),
     * model içeriğini bulup standart konuma taşır.
     */
    private fun normalizeModelDir() {
        if (hasModelContent(modelDir)) return
        val candidate = findModelContent(modelsRoot, depth = 3) ?: return
        if (candidate == modelDir) return
        modelDir.deleteRecursively()
        if (!candidate.renameTo(modelDir)) {
            candidate.copyRecursively(modelDir, overwrite = true)
            candidate.deleteRecursively()
        }
    }

    private fun hasModelContent(dir: File): Boolean =
        File(dir, "am").isDirectory || File(dir, "conf").isDirectory

    private fun findModelContent(root: File, depth: Int): File? {
        if (depth < 0) return null
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return null
        for (d in dirs) {
            if (hasModelContent(d)) return d
            findModelContent(d, depth - 1)?.let { return it }
        }
        return null
    }

    companion object {
        // Alpha Cephei'nin resmî küçük Türkçe modeli (Apache 2.0 lisanslı)
        const val MODEL_NAME = "vosk-model-small-tr-0.3"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SIZE_MB = 35

        // Sunucudaki bot koruması varsayılan "Java/..." kimliğini engelleyebildiği
        // için tarayıcı benzeri bir kimlik kullanılır
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
