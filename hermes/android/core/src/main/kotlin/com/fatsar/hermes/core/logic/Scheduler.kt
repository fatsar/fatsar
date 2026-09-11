package com.fatsar.hermes.core.logic

import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ScheduleMode

/**
 * Zamanlanmış bot çalıştırmalarının saf (yan etkisiz) karar mantığı.
 * Saat dilimi, çağıran tarafından milisaniye cinsinden ofset olarak verilir.
 */
object Scheduler {

    const val DAY_MS = 24L * 60 * 60 * 1000

    fun isDue(bot: BotSpec, lastRun: Long?, now: Long, zoneOffsetMs: Int): Boolean {
        if (!bot.enabled || !bot.schedule.isActive) return false
        return when (bot.schedule.mode) {
            ScheduleMode.OFF -> false
            ScheduleMode.INTERVAL -> {
                val every = bot.schedule.everyMinutes.coerceAtLeast(1) * 60_000L
                lastRun == null || now - lastRun >= every
            }
            ScheduleMode.DAILY -> {
                val target = todayTarget(bot, now, zoneOffsetMs)
                now >= target && (lastRun == null || lastRun < target)
            }
        }
    }

    fun dueBots(
        bots: List<BotSpec>,
        lastRuns: Map<String, Long>,
        now: Long,
        zoneOffsetMs: Int,
    ): List<BotSpec> = bots.filter { isDue(it, lastRuns[it.id], now, zoneOffsetMs) }

    /** Bir sonraki çalışma zamanı (epoch ms) — arayüzde "sonraki: 14:30" göstermek için. */
    fun nextRun(bot: BotSpec, lastRun: Long?, now: Long, zoneOffsetMs: Int): Long? {
        if (!bot.enabled || !bot.schedule.isActive) return null
        return when (bot.schedule.mode) {
            ScheduleMode.OFF -> null
            ScheduleMode.INTERVAL -> {
                val every = bot.schedule.everyMinutes.coerceAtLeast(1) * 60_000L
                if (lastRun == null) now else lastRun + every
            }
            ScheduleMode.DAILY -> {
                val target = todayTarget(bot, now, zoneOffsetMs)
                if (now < target && (lastRun == null || lastRun < target)) target else target + DAY_MS
            }
        }
    }

    /** Bugünün yerel saatiyle hedef anı (epoch ms). */
    private fun todayTarget(bot: BotSpec, now: Long, zoneOffsetMs: Int): Long {
        val local = now + zoneOffsetMs
        val startOfLocalDay = local - Math.floorMod(local, DAY_MS)
        val minuteOfDay = bot.schedule.atHour.coerceIn(0, 23) * 60L + bot.schedule.atMinute.coerceIn(0, 59)
        return startOfLocalDay + minuteOfDay * 60_000L - zoneOffsetMs
    }

    /** Arka plan denetleyicisinin ne sıklıkta uyanacağı (ms). */
    fun tickInterval(bots: List<BotSpec>): Long {
        val active = bots.filter { it.enabled && it.schedule.isActive }
        if (active.isEmpty()) return 5 * 60_000L
        val minInterval = active
            .filter { it.schedule.mode == ScheduleMode.INTERVAL }
            .minOfOrNull { it.schedule.everyMinutes.coerceAtLeast(1) * 60_000L }
            ?: 60_000L
        return minInterval.coerceIn(60_000L, 15 * 60_000L)
    }
}
