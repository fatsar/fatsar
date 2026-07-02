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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kayıtlı kartvizitlerden Excel (.xlsx) dosyası üretir; dosyayı Gmail ile
 * gönderme ve İndirilenler klasörüne kaydetme işlemlerini yönetir.
 */
object ExcelManager {

    const val FILE_NAME = "kartvizitler.xlsx"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Excel dosyasını güncel kayıtlarla yeniden oluşturur. */
    fun regenerate(context: Context): File {
        val records = ContactRepository.getAll(context)
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, FILE_NAME)

        val headers = listOf(
            context.getString(R.string.col_name),
            context.getString(R.string.col_title),
            context.getString(R.string.col_company),
            context.getString(R.string.col_phone),
            context.getString(R.string.col_email),
            context.getString(R.string.col_website),
            context.getString(R.string.col_address),
            context.getString(R.string.col_notes),
            context.getString(R.string.col_created)
        )
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        val rows = records.map { r ->
            listOf(
                r.name,
                r.title,
                r.company,
                r.phones.joinToString(", "),
                r.emails.joinToString(", "),
                r.website,
                r.address,
                r.notes,
                dateFormat.format(Date(r.createdAt))
            )
        }
        FileOutputStream(file).use { XlsxWriter.write(headers, rows, "Kartvizitler", it) }
        return file
    }

    /**
     * Excel dosyasını e-posta uygulamasıyla (ör. Gmail) paylaşır. Gmail,
     * telefondaki Google hesabıyla gönderir; alıcı olarak kendi adresinizi
     * seçerseniz dosya Gmail hesabınızda saklanmış olur.
     */
    fun buildEmailIntent(context: Context, recipient: String?): Intent {
        val file = regenerate(context)
        val uri = shareUri(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!recipient.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.trim()))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.email_body))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.send_excel_chooser))
    }

    /** Excel dosyasını cihazın İndirilenler klasörüne kopyalar; görünen yolu döndürür. */
    fun saveToDownloads(context: Context): String? {
        val file = regenerate(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, MIME_XLSX)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return null
            "${Environment.DIRECTORY_DOWNLOADS}/$FILE_NAME"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            val dest = File(dir, FILE_NAME)
            file.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }

    private fun shareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
