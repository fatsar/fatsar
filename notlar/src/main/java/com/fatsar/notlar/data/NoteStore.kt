package com.fatsar.notlar.data

import android.content.Context
import android.util.Log
import com.fatsar.notlar.model.Note
import java.io.File
import java.util.UUID

/**
 * Notların cihaz üzerindeki kalıcı deposu. Bulut, hesap ya da internet yok:
 * her şey uygulamanın kendi klasöründeki `notlar.json` dosyasında durur.
 *
 * Yazma atomiktir (önce geçici dosya, sonra yeniden adlandırma) ve bir önceki
 * sürüm `.bak` olarak saklanır; böylece yazma sırasında kapanan uygulama
 * yüzünden not kaybı olmaz.
 */
class NoteStore(context: Context) {

    private val dir: File = context.filesDir
    private val file = File(dir, FILE_NAME)
    private val backup = File(dir, "$FILE_NAME.bak")
    private val temp = File(dir, "$FILE_NAME.tmp")

    /** Kalemle çizilen katmanların klasörü. */
    val sketchDir: File = File(dir, "cizimler")

    fun load(): List<Note> {
        val notes = readFile(file)
        if (notes.isNotEmpty() || !backup.exists()) return notes
        Log.w(TAG, "Ana dosya okunamadı, yedekten dönülüyor")
        return readFile(backup)
    }

    fun save(notes: List<Note>) {
        try {
            temp.writeText(NoteSerializer.toJson(notes))
            if (file.exists()) {
                backup.delete()
                file.copyTo(backup, overwrite = true)
            }
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notlar kaydedilemedi", e)
        }
    }

    fun newNote(now: Long = System.currentTimeMillis(), body: String = ""): Note =
        Note(id = UUID.randomUUID().toString(), body = body, createdAt = now, updatedAt = now)

    // --- Kalem katmanı dosyaları ---

    fun sketchStrokeFile(name: String): File = File(ensureSketchDir(), "$name.json")

    fun sketchImageFile(name: String): File = File(ensureSketchDir(), "$name.png")

    fun newSketchName(): String = "cizim-" + UUID.randomUUID().toString().take(8)

    fun deleteSketch(name: String?) {
        if (name.isNullOrBlank()) return
        sketchStrokeFile(name).delete()
        sketchImageFile(name).delete()
    }

    private fun ensureSketchDir(): File {
        if (!sketchDir.exists()) sketchDir.mkdirs()
        return sketchDir
    }

    private fun readFile(source: File): List<Note> = try {
        if (source.exists()) NoteSerializer.fromJson(source.readText()) else emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "Notlar okunamadı: ${source.name}", e)
        emptyList()
    }

    companion object {
        private const val TAG = "NoteStore"
        private const val FILE_NAME = "notlar.json"
    }
}
