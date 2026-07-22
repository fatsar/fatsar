package com.fatsar.kartvizit.data

import android.content.Context
import com.fatsar.kartvizit.model.ContactRecord
import org.json.JSONArray
import java.io.File

/**
 * Kayıtları uygulamanın özel depolama alanındaki bir JSON dosyasında tutar.
 * Tüm veriler cihazda saklanır; internet bağlantısı gerekmez.
 */
object ContactRepository {

    private const val FILE_NAME = "contacts.json"

    private var cache: MutableList<ContactRecord>? = null

    @Synchronized
    fun getAll(context: Context): List<ContactRecord> =
        load(context).sortedByDescending { it.createdAt }

    @Synchronized
    fun get(context: Context, id: String): ContactRecord? =
        load(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, record: ContactRecord) {
        val list = load(context)
        val index = list.indexOfFirst { it.id == record.id }
        if (index >= 0) list[index] = record else list.add(record)
        persist(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val list = load(context)
        list.removeAll { it.id == id }
        persist(context, list)
    }

    @Synchronized
    fun clearCacheForTest() {
        cache = null
    }

    /** Tüm kayıtları yedek için JSON dizisi olarak verir. */
    @Synchronized
    fun exportJson(context: Context): String {
        val array = JSONArray()
        load(context).forEach { array.put(it.toJson()) }
        return array.toString()
    }

    /**
     * Yedekteki kayıtları mevcut listeye katar: aynı kimlikli kayıt güncellenir,
     * yeni kimlik eklenir. Geri yüklenen kayıt sayısını döndürür.
     */
    @Synchronized
    fun importJson(context: Context, json: String): Int {
        val array = JSONArray(json)
        val list = load(context)
        var count = 0
        for (i in 0 until array.length()) {
            val record = ContactRecord.fromJson(array.getJSONObject(i))
            val index = list.indexOfFirst { it.id == record.id }
            if (index >= 0) list[index] = record else list.add(record)
            count++
        }
        persist(context, list)
        return count
    }

    private fun load(context: Context): MutableList<ContactRecord> {
        cache?.let { return it }
        val file = File(context.filesDir, FILE_NAME)
        val list = mutableListOf<ContactRecord>()
        if (file.exists()) {
            runCatching {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) {
                    list.add(ContactRecord.fromJson(array.getJSONObject(i)))
                }
            }
        }
        cache = list
        return list
    }

    private fun persist(context: Context, list: MutableList<ContactRecord>) {
        cache = list
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        File(context.filesDir, FILE_NAME).writeText(array.toString())
    }
}
