package com.fatsar.notlar.util

import android.content.Context
import android.text.format.DateFormat
import com.fatsar.notlar.R
import java.util.Date

/** [RelativeTime] kararını kullanıcıya gösterilecek metne çevirir. */
object TimeText {

    fun format(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return ""
        return when (val label = RelativeTime.describe(now, millis)) {
            RelativeTime.Label.JustNow -> context.getString(R.string.time_just_now)
            is RelativeTime.Label.Minutes -> context.getString(R.string.time_minutes, label.value)
            is RelativeTime.Label.Hours -> context.getString(R.string.time_hours, label.value)
            is RelativeTime.Label.Yesterday ->
                context.getString(R.string.time_yesterday, time(context, label.millis))
            is RelativeTime.Label.DaysAgo -> context.getString(R.string.time_days, label.value)
            is RelativeTime.Label.OnDate -> date(context, label.millis)
        }
    }

    private fun time(context: Context, millis: Long): String =
        DateFormat.getTimeFormat(context).format(Date(millis))

    private fun date(context: Context, millis: Long): String =
        DateFormat.getMediumDateFormat(context).format(Date(millis))
}
