package com.fatsar.kartvizit.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fatsar.kartvizit.data.ContactRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Taranan kartvizit verisini bir bulut/klasör hedefine yedekler. Belirli bir
 * bulut SDK'sına (Drive API vb.) bağlanmak yerine Android'in **Depolama Erişim
 * Çerçevesi** (SAF) kullanılır: kullanıcı Google Drive, OneDrive, Dropbox ya da
 * telefon belleğinden herhangi bir klasör seçer; yedek oraya yazılır. Böylece
 * uygulama çevrimdışı ve anahtarsız kalır, senkronizasyonu seçilen bulut
 * uygulamasının kendisi yapar.
 *
 * Yedek biçimi: tek bir **.zip** paketi — içinde `kartvizitler.json` (birebir
 * geri yüklenebilir veri), `kartvizitler.xlsx` (okunabilir tablo) ve
 * `kartvizitler.vcf` (rehbere aktarılabilir) bulunur.
 */
object CloudBackup {

    private const val PREFS = "settings"
    private const val PREF_TREE = "backup_tree_uri"
    private const val PREF_AUTO = "backup_auto"

    const val MIME_ZIP = "application/zip"
    private const val JSON_ENTRY = "kartvizitler.json"
    private const val AUTO_BACKUP_NAME = "KartCep-oto-yedek.zip"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Seçili yedekleme klasörü (SAF ağaç URI'si); seçilmediyse null. */
    fun backupFolder(context: Context): Uri? =
        prefs(context).getString(PREF_TREE, null)?.let(Uri::parse)

    fun setBackupFolder(context: Context, uri: Uri) {
        prefs(context).edit().putString(PREF_TREE, uri.toString()).apply()
    }

    fun isAutoBackup(context: Context): Boolean =
        prefs(context).getBoolean(PREF_AUTO, false)

    fun setAutoBackup(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AUTO, enabled).apply()
    }

    /**
     * Seçili klasöre bir .zip yedek yazar; oluşan dosya adını döndürür. Klasör
     * seçilmemişse, yazılamıyorsa ya da hata olursa null döner.
     *
     * @param rolling elle yedeklemede her seferinde tarih damgalı yeni dosya
     * yazılır (geçmiş korunur). Otomatik yedeklemede ise tek bir sabit dosya
     * (KartCep-oto-yedek.zip) üzerine yazılır; böylece klasör dolup taşmaz.
     */
    fun writeBackup(context: Context, rolling: Boolean = false): String? {
        val treeUri = backupFolder(context) ?: return null
        val dir = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return null
        if (!dir.canWrite()) return null

        val name = if (rolling) AUTO_BACKUP_NAME else "KartCep-yedek-${timestamp()}.zip"
        // Sabit adlı otomatik yedekte, SAF'ın "(1)" kopyası üretmemesi için
        // aynı adlı eski dosya önce silinir.
        if (rolling) runCatching { dir.findFile(name)?.delete() }

        val doc = dir.createFile(MIME_ZIP, name) ?: return null
        return try {
            val bytes = buildZipBytes(context)
            context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
                ?: run { doc.delete(); return null }
            doc.name ?: name
        } catch (e: Exception) {
            runCatching { doc.delete() }
            null
        }
    }

    /** Otomatik yedekleme açık ve klasör seçiliyse sessizce (tek dosyaya) yedek alır. */
    fun maybeAutoBackup(context: Context) {
        if (isAutoBackup(context) && backupFolder(context) != null) {
            runCatching { writeBackup(context, rolling = true) }
        }
    }

    /**
     * Bir yedekten (.zip ya da düz .json) kayıtları geri yükler; geri yüklenen
     * kayıt sayısını döndürür, hata olursa -1.
     */
    fun restore(context: Context, uri: Uri): Int {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { readJson(it) }
            if (json.isNullOrBlank()) -1 else ContactRepository.importJson(context, json)
        } catch (e: Exception) {
            -1
        }
    }

    private fun buildZipBytes(context: Context): ByteArray {
        val json = ContactRepository.exportJson(context)
        val xlsx = ExportManager.regenerateExcel(context)
        val vcf = ExportManager.regenerateVcf(context)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ExportManager.EXCEL_FILE_NAME))
            xlsx.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ExportManager.VCF_FILE_NAME))
            vcf.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** Akıştaki veriyi okur: zip ise içindeki .json girişini, değilse düz metni. */
    private fun readJson(input: InputStream): String? {
        val bytes = input.readBytes()
        val isZip = bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        if (isZip) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json")) {
                        return zin.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zin.nextEntry
                }
            }
            return null
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
}
