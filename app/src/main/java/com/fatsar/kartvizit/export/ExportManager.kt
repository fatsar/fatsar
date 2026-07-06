package com.fatsar.kartvizit.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.fatsar.kartvizit.R
import com.fatsar.kartvizit.data.ContactRepository
import com.fatsar.kartvizit.model.PhoneType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kayıtlardan Excel (.xlsx) ve rehber (.vcf) dosyaları üretir; bunları
 * Gmail ile gönderme, paylaşma ve İndirilenler klasörüne kaydetme
 * işlemlerini yönetir.
 */
object ExportManager {

    const val EXCEL_FILE_NAME = "kartvizitler.xlsx"
    const val VCF_FILE_NAME = "kartvizitler.vcf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_VCF = "text/x-vcard"

    /** Excel dosyasını güncel kayıtlarla yeniden oluşturur. */
    fun regenerateExcel(context: Context): File {
        val records = ContactRepository.getAll(context)
        val file = exportFile(context, EXCEL_FILE_NAME)

        val headers = listOf(
            context.getString(R.string.col_name),
            context.getString(R.string.col_title),
            context.getString(R.string.col_company),
            context.getString(R.string.col_mobile),
            context.getString(R.string.col_work_phone),
            context.getString(R.string.col_fax),
            context.getString(R.string.col_home_phone),
            context.getString(R.string.col_email),
            context.getString(R.string.col_website),
            context.getString(R.string.col_address),
            context.getString(R.string.col_category),
            context.getString(R.string.col_notes),
            context.getString(R.string.col_created)
        )
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        val rows = records.map { r ->
            // OTHER türü, karşılığı olmayan numaraları da göstermek için işe eklenir
            val work = (r.phonesOf(PhoneType.WORK) + r.phonesOf(PhoneType.OTHER))
            listOf(
                r.name,
                r.title,
                r.company,
                r.phonesOf(PhoneType.MOBILE).joinToString(", "),
                work.joinToString(", "),
                r.phonesOf(PhoneType.FAX).joinToString(", "),
                r.phonesOf(PhoneType.HOME).joinToString(", "),
                r.emails.joinToString(", "),
                r.website,
                r.address,
                r.category,
                r.notes,
                dateFormat.format(Date(r.createdAt))
            )
        }
        FileOutputStream(file).use { XlsxWriter.write(headers, rows, "Kartvizitler", it) }
        return file
    }

    /** Rehber (.vcf) dosyasını güncel kayıtlarla yeniden oluşturur. */
    fun regenerateVcf(context: Context): File {
        val records = ContactRepository.getAll(context)
        val file = exportFile(context, VCF_FILE_NAME)
        FileOutputStream(file).use { VcfWriter.write(records, it) }
        return file
    }

    /**
     * Excel dosyasını e-posta uygulamasıyla (ör. Gmail) paylaşır. Gmail,
     * telefondaki Google hesabıyla gönderir; alıcı olarak kendi adresinizi
     * seçerseniz dosya Gmail hesabınızda saklanmış olur.
     */
    fun buildEmailIntent(context: Context, recipient: String?): Intent {
        val file = regenerateExcel(context)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, shareUri(context, file))
            if (!recipient.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.trim()))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.email_body))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.send_excel_chooser))
    }

    /**
     * Rehber (.vcf) dosyasını paylaşır (WhatsApp, e-posta, Bluetooth vb.).
     * Alıcı dosyaya dokunarak kişileri kendi rehberine aktarabilir.
     */
    fun buildVcfShareIntent(context: Context): Intent {
        val file = regenerateVcf(context)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_VCF
            putExtra(Intent.EXTRA_STREAM, shareUri(context, file))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.vcf_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_vcf_chooser))
    }

    /** Excel dosyasını cihazın İndirilenler klasörüne kopyalar; görünen yolu döndürür. */
    fun saveToDownloads(context: Context): String? {
        val file = regenerateExcel(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, EXCEL_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, MIME_XLSX)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return null
            "${Environment.DIRECTORY_DOWNLOADS}/$EXCEL_FILE_NAME"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            val dest = File(dir, EXCEL_FILE_NAME)
            file.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }

    private fun exportFile(context: Context, name: String): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    private fun shareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
