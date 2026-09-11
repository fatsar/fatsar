package com.fatsar.hermes.core.backend

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

internal fun JsonObject.str(key: String, def: String = ""): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: def

internal fun JsonObject.int(key: String, def: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: def

internal fun JsonObject.long(key: String, def: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong() ?: def

internal fun JsonObject.bool(key: String, def: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.contentOrNull?.lowercase()?.let {
        when (it) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    } ?: def

/** İç içe alan okuma: obj.path("message", "content"). */
internal fun JsonObject.path(vararg keys: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = (cur as? JsonObject)?.get(k) ?: return null
    }
    return cur
}

internal fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
