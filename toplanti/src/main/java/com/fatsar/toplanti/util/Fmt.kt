package com.fatsar.toplanti.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {

    private val TR = Locale("tr", "TR")

    fun date(millis: Long): String =
        SimpleDateFormat("d MMMM yyyy", TR).format(Date(millis))

    fun dateTime(millis: Long): String =
        SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(Date(millis))

    /** 65_000 → "01:05", 3_725_000 → "1:02:05" */
    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(TR, "%d:%02d:%02d", h, m, s)
        else String.format(TR, "%02d:%02d", m, s)
    }

    /** Transkript zaman damgası: [12:34] */
    fun timestamp(ms: Long): String = "[" + duration(ms) + "]"

    fun fileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(TR, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(TR, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** Dosya adı için güvenli hale getirir. */
    fun safeFileName(s: String): String =
        s.replace(Regex("[^\\p{L}\\p{Nd} _-]"), "").trim().replace(' ', '_').take(60)
            .ifBlank { "toplanti" }
}
