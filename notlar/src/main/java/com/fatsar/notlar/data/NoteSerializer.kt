package com.fatsar.notlar.data

import com.fatsar.notlar.model.Note
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notların JSON'a çevrimi. Dosya biçimi bilerek düz ve okunabilir tutuldu:
 * kullanıcı isterse notlarını dışa aktarıp başka bir yerde okuyabilsin.
 */
object NoteSerializer {

    const val VERSION = 1

    fun toJson(notes: List<Note>): String {
        val array = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("id", note.id)
            obj.put("body", note.body)
            obj.put("createdAt", note.createdAt)
            obj.put("updatedAt", note.updatedAt)
            if (note.pinned) obj.put("pinned", true)
            if (note.sketchName != null) obj.put("sketch", note.sketchName)
            array.put(obj)
        }
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("notes", array)
        return root.toString(2)
    }

    /** Bozuk/eksik alanlara dayanıklıdır: okunamayan kayıt atlanır, gerisi kurtarılır. */
    fun fromJson(json: String): List<Note> {
        if (json.isBlank()) return emptyList()
        val array = try {
            val trimmed = json.trimStart()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("notes") ?: JSONArray()
        } catch (e: Exception) {
            return emptyList()
        }
        val notes = ArrayList<Note>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val created = obj.optLong("createdAt", 0L)
            val updated = obj.optLong("updatedAt", created)
            notes.add(
                Note(
                    id = id,
                    body = obj.optString("body", ""),
                    createdAt = created,
                    updatedAt = updated,
                    pinned = obj.optBoolean("pinned", false),
                    sketchName = obj.optString("sketch", "").takeIf { it.isNotBlank() }
                )
            )
        }
        return notes
    }
}
