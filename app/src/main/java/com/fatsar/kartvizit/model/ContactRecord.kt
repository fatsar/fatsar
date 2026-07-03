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
    var phones: List<String> = emptyList(),
    var emails: List<String> = emptyList(),
    var website: String = "",
    var address: String = "",
    var notes: String = "",
    var category: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var addedToContacts: Boolean = false
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("title", title)
        put("company", company)
        put("phones", JSONArray(phones))
        put("emails", JSONArray(emails))
        put("website", website)
        put("address", address)
        put("notes", notes)
        put("category", category)
        put("createdAt", createdAt)
        put("addedToContacts", addedToContacts)
    }

    companion object {
        fun fromJson(o: JSONObject): ContactRecord = ContactRecord(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            title = o.optString("title"),
            company = o.optString("company"),
            phones = o.optJSONArray("phones").toStringList(),
            emails = o.optJSONArray("emails").toStringList(),
            website = o.optString("website"),
            address = o.optString("address"),
            notes = o.optString("notes"),
            category = o.optString("category"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            addedToContacts = o.optBoolean("addedToContacts", false)
        )

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
        }
    }
}
