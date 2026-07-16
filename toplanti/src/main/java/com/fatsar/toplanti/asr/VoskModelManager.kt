package com.fatsar.toplanti.asr

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Türkçe ve İngilizce konuşma tanıma modellerini yönetir.
 *
 * Modeller normalde APK içinde paketlenmiş gelir (CI derlemede assets/models/
 * altına koyar) ve ilk kullanımda uygulama deposuna kopyalanır — cihazda ağ
 * erişimi gerekmez. Paket içermeyen geliştirici derlemeleri için indirme ve
 * elle ZIP kurulumu yedek yol olarak korunur. Ses verisi hiçbir zaman cihaz
 * dışına gönderilmez.
 */
class VoskModelManager(private val context: Context) {

    private val modelsRoot = File(context.filesDir, "models").apply { mkdirs() }

    fun modelName(lang: String): String = if (lang == LANG_EN) EN_MODEL else TR_MODEL

    private fun modelDir(lang: String): File = File(modelsRoot, modelName(lang))

    fun isInstalled(lang: String): Boolean = hasModelContent(modelDir(lang))

    fun installedModelDir(lang: String): File? = modelDir(lang).takeIf { hasModelContent(it) }

    /**
     * APK içinde bu dil için paketlenmiş model ZIP'i var mı?
     * (AssetManager.list yerine doğrudan open denenir; list bazı cihazlarda
     * güvenilir çalışmadığından modeller tek ZIP dosyası olarak paketlenir.)
     */
    fun hasBundledModel(lang: String): Boolean = runCatching {
        context.assets.open(bundledZipPath(lang)).use { true }
    }.getOrDefault(false)

    /** Kurulu ya da paketten kurulabilir durumda mı? */
    fun isReady(lang: String): Boolean = isInstalled(lang) || hasBundledModel(lang)

    /**
     * Modeli kullanılabilir hale getirir: kuruluysa döner; APK paketindeyse
     * ZIP'i açıp kurar; hiçbiri yoksa hata fırlatır (indirme/ZIP yedek yolu ayrıdır).
     */
    @Throws(IOException::class)
    fun ensureInstalled(lang: String, onProgress: (Int) -> Unit) {
        if (isInstalled(lang)) return
        val assetPath = bundledZipPath(lang)
        val tmp = File(modelsRoot, "bundled-${modelName(lang)}.zip")
        try {
            context.assets.open(assetPath).use { ins ->
                tmp.outputStream().use { ins.copyTo(it) }
            }
            onProgress(40)
            installZip(tmp, onProgress)
        } catch (e: FileNotFoundException) {
            throw IOException("Model paketi APK içinde bulunamadı: $assetPath", e)
        } finally {
            tmp.delete()
        }
        if (!isInstalled(lang)) {
            throw IOException("Paketlenmiş model kurulamadı (${modelName(lang)})")
        }
    }

    private fun bundledZipPath(lang: String): String = "$ASSET_ROOT/${modelName(lang)}.zip"

    // ---- Yedek yol 1: çalışma anında indirme ----

    /** Modeli indirir ve kurar. [onProgress] 0..100 (indirme 0..80, kurulum 80..100). */
    @Throws(IOException::class)
    fun download(lang: String, onProgress: (Int) -> Unit) {
        val name = modelName(lang)
        val zipFile = File(modelsRoot, "$name.zip")
        try {
            fetch("$MODEL_BASE_URL/$name.zip", zipFile, onProgress)
            installZip(zipFile, onProgress)
        } finally {
            zipFile.delete()
        }
    }

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

    // ---- Yedek yol 2: kullanıcının elle seçtiği ZIP ----

    /**
     * Elle seçilen model ZIP'ini kurar; kurulan dili ("tr"/"en") döndürür.
     * Dil, arşivdeki model klasörünün adından anlaşılır.
     */
    @Throws(IOException::class)
    fun installFromStream(input: InputStream, onProgress: (Int) -> Unit): String {
        val zipFile = File(modelsRoot, "manual-model.zip")
        try {
            zipFile.outputStream().use { input.copyTo(it) }
            onProgress(50)
            return installZip(zipFile, onProgress)
        } finally {
            zipFile.delete()
        }
    }

    private fun installZip(zipFile: File, onProgress: (Int) -> Unit): String {
        if (!looksLikeZip(zipFile)) {
            // Tipik neden: sunucunun bot koruması ZIP yerine HTML sayfası döndürdü
            throw IOException(
                "Sunucu ZIP yerine farklı bir içerik döndürdü (erişim engeli olabilir). " +
                    "Yeniden deneyin ya da modeli tarayıcınızla indirip \"ZIP seç\" ile kurun."
            )
        }
        unzip(zipFile, onProgress)
        val lang = normalizeModelDirs()
            ?: throw IOException("Model arşivi beklenen yapıda değil")
        onProgress(100)
        return lang
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
     * Açılan arşivdeki model içeriğini bulur, adından dilini anlar ve standart
     * klasör adına taşır. Kurulan dili döndürür; içerik yoksa null.
     */
    private fun normalizeModelDirs(): String? {
        val candidate = findModelContent(modelsRoot, depth = 3) ?: return null
        val lang = if (candidate.name.contains("-en")) LANG_EN else LANG_TR
        val target = modelDir(lang)
        if (candidate == target) return lang
        target.deleteRecursively()
        if (!candidate.renameTo(target)) {
            candidate.copyRecursively(target, overwrite = true)
            candidate.deleteRecursively()
        }
        return lang
    }

    private fun hasModelContent(dir: File): Boolean =
        File(dir, "am").isDirectory || File(dir, "conf").isDirectory

    private fun findModelContent(root: File, depth: Int): File? {
        if (depth < 0) return null
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return null
        // Standart konumda zaten kurulu olanlar aday değildir; yeni açılanı ara
        for (d in dirs) {
            if (hasModelContent(d) && d.name != TR_MODEL && d.name != EN_MODEL) return d
            findModelContent(d, depth - 1)?.let { return it }
        }
        // Yalnızca standart adla açıldıysa onu kabul et
        return dirs.firstOrNull { hasModelContent(it) }
    }

    companion object {
        const val LANG_TR = "tr"
        const val LANG_EN = "en"
        const val LANG_AUTO = "auto"

        // Alpha Cephei'nin resmî küçük modelleri (Apache 2.0 lisanslı)
        const val TR_MODEL = "vosk-model-small-tr-0.3"
        const val EN_MODEL = "vosk-model-small-en-us-0.15"
        const val MODEL_BASE_URL = "https://alphacephei.com/vosk/models"
        const val MODEL_SIZE_MB = 40
        private const val ASSET_ROOT = "models"

        /** Bu toplantı dili için gereken model dilleri. */
        fun requiredLanguages(meetingLanguage: String): List<String> = when (meetingLanguage) {
            LANG_EN -> listOf(LANG_EN)
            LANG_TR -> listOf(LANG_TR)
            else -> listOf(LANG_TR, LANG_EN) // otomatik algılama iki modeli de kullanır
        }

        // Sunucudaki bot koruması varsayılan "Java/..." kimliğini engelleyebildiği
        // için tarayıcı benzeri bir kimlik kullanılır
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
