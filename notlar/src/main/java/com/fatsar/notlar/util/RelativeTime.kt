package com.fatsar.notlar.util

import java.util.Calendar
import java.util.TimeZone

/**
 * "Son düzenlenme" damgasının insan diline çevrimi. Metnin kendisi Android
 * tarafında kaynak dizgilerle üretilir; burada yalnızca hangi biçimin
 * kullanılacağına karar verilir (böylece takvim mantığı testlenebilir).
 */
object RelativeTime {

    sealed class Label {
        /** Bir dakikadan yeni. */
        object JustNow : Label()
        data class Minutes(val value: Int) : Label()
        data class Hours(val value: Int) : Label()
        /** Dün — saatiyle birlikte gösterilir. */
        data class Yesterday(val millis: Long) : Label()
        data class DaysAgo(val value: Int) : Label()
        /** Bir haftadan eski: tam tarih. */
        data class OnDate(val millis: Long) : Label()
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    fun describe(now: Long, then: Long, zone: TimeZone = TimeZone.getDefault()): Label {
        val diff = now - then
        if (diff < MINUTE) return Label.JustNow
        if (diff < HOUR) return Label.Minutes((diff / MINUTE).toInt())

        val dayDiff = calendarDaysBetween(then, now, zone)
        return when {
            dayDiff <= 0 -> Label.Hours((diff / HOUR).toInt().coerceAtLeast(1))
            dayDiff == 1 -> Label.Yesterday(then)
            dayDiff < 7 -> Label.DaysAgo(dayDiff)
            else -> Label.OnDate(then)
        }
    }

    /** Takvim günü farkı (saat farkı değil): dün 23:50 → bugün 00:10 = 1 gün. */
    private fun calendarDaysBetween(from: Long, to: Long, zone: TimeZone): Int {
        val a = midnight(from, zone)
        val b = midnight(to, zone)
        return ((b - a) / 86_400_000L).toInt()
    }

    private fun midnight(millis: Long, zone: TimeZone): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
