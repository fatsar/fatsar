package com.fatsar.kartvizit.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Taranan bir kartvizitten elde edilen kişi kaydı. */
data class ContactRecord(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var title: String = "",
    var company: String = "",
    var phones: List<TypedPhone> = emptyList(),
    var emails: List<String> = emptyList(),
    var website: String = "",
    var address: String = "",
    var notes: String = "",
    var category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var addedToContacts: Boolean = false,
    var lastSentAt: Long = 0L
) {

    /** Belirtilen türdeki numaraları döndürür. */
    fun phonesOf(type: PhoneType): List<String> =
        phones.filter { it.type == type }.map { it.number }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("title", title)
        put("company", company)
        put("phones", JSONArray().apply { phones.forEach { put(it.toJson()) } })
        put("emails", JSONArray(emails))
        put("website", website)
        put("address", address)
        put("notes", notes)
        put("category", category)
        put("createdAt", createdAt)
        put("addedToContacts", addedToContacts)
        put("lastSentAt", lastSentAt)
    }

    companion object {
        fun fromJson(o: JSONObject): ContactRecord = ContactRecord(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            title = o.optString("title"),
            company = o.optString("company"),
            phones = parsePhones(o.optJSONArray("phones")),
            emails = o.optJSONArray("emails").toStringList(),
            website = o.optString("website"),
            address = o.optString("address"),
            notes = o.optString("notes"),
            category = o.optString("category"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            addedToContacts = o.optBoolean("addedToContacts", false),
            lastSentAt = o.optLong("lastSentAt", 0L)
        )

        /**
         * Telefonları okur. Yeni biçim {number,type} nesneleridir; eski
         * kayıtlar (düz metin dizisi) da geriye dönük uyumlu okunur.
         */
        private fun parsePhones(array: JSONArray?): List<TypedPhone> {
            if (array == null) return emptyList()
            val result = mutableListOf<TypedPhone>()
            for (i in 0 until array.length()) {
                when (val item = array.opt(i)) {
                    is JSONObject -> {
                        val phone = TypedPhone.fromJson(item)
                        if (phone.number.isNotBlank()) result.add(phone)
                    }
                    is String -> if (item.isNotBlank()) result.add(TypedPhone(item, PhoneType.OTHER))
                }
            }
            return result
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
        }
    }
}
