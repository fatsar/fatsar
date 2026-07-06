package com.fatsar.kartvizit.model

import org.json.JSONObject

/** Telefon numarası türü. */
enum class PhoneType {
    MOBILE, WORK, FAX, HOME, OTHER;

    /** Türkçe görünen etiket (arayüz ve dışa aktarma için). */
    fun trLabel(): String = when (this) {
        MOBILE -> "Cep"
        WORK -> "İş"
        FAX -> "Faks"
        HOME -> "Ev"
        OTHER -> "Telefon"
    }

    companion object {
        fun from(name: String?): PhoneType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OTHER
    }
}

/** Türü ile birlikte bir telefon numarası. */
data class TypedPhone(val number: String, val type: PhoneType = PhoneType.OTHER) {

    fun toJson(): JSONObject = JSONObject()
        .put("number", number)
        .put("type", type.name)

    companion object {
        fun fromJson(o: JSONObject): TypedPhone =
            TypedPhone(o.optString("number"), PhoneType.from(o.optString("type")))
    }
}
