package com.fatsar.hermes.core

import com.fatsar.hermes.core.logic.Scheduler
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.Schedule
import com.fatsar.hermes.core.model.ScheduleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchedulerTest {

    private val tr = 3 * 60 * 60 * 1000 // Türkiye: UTC+3

    private fun bot(schedule: Schedule, enabled: Boolean = true) =
        BotSpec(id = "b1", name = "Bekçi", schedule = schedule, enabled = enabled)

    @Test
    fun `aralikli bot ilk kez hemen calisir`() {
        val b = bot(Schedule(ScheduleMode.INTERVAL, everyMinutes = 60, prompt = "kontrol et"))
        assertTrue(Scheduler.isDue(b, null, 1_000_000L, tr))
    }

    @Test
    fun `aralik dolmadan tekrar calismaz`() {
        val b = bot(Schedule(ScheduleMode.INTERVAL, everyMinutes = 60, prompt = "kontrol et"))
        val now = 10_000_000L
        assertFalse(Scheduler.isDue(b, now - 59 * 60_000L, now, tr))
        assertTrue(Scheduler.isDue(b, now - 60 * 60_000L, now, tr))
    }

    @Test
    fun `istem bossa zamanlama calismaz`() {
        val b = bot(Schedule(ScheduleMode.INTERVAL, everyMinutes = 5, prompt = ""))
        assertFalse(Scheduler.isDue(b, null, 1_000L, tr))
    }

    @Test
    fun `kapali bot calismaz`() {
        val b = bot(Schedule(ScheduleMode.INTERVAL, everyMinutes = 5, prompt = "x"), enabled = false)
        assertFalse(Scheduler.isDue(b, null, 1_000L, tr))
    }

    @Test
    fun `gunluk bot yerel saatte tetiklenir`() {
        // 1 Ocak 1970, yerel saat (UTC+3) 08:29 ve 08:31
        val target = Schedule(ScheduleMode.DAILY, atHour = 8, atMinute = 30, prompt = "özet çıkar")
        val b = bot(target)
        val localDayStart = -tr.toLong() // yerel gün başlangıcının UTC karşılığı
        val at0829 = localDayStart + (8 * 60 + 29) * 60_000L
        val at0831 = localDayStart + (8 * 60 + 31) * 60_000L
        assertFalse(Scheduler.isDue(b, null, at0829, tr))
        assertTrue(Scheduler.isDue(b, null, at0831, tr))
    }

    @Test
    fun `gunluk bot ayni gun iki kez calismaz`() {
        val b = bot(Schedule(ScheduleMode.DAILY, atHour = 8, atMinute = 30, prompt = "özet"))
        val localDayStart = -tr.toLong()
        val at0831 = localDayStart + (8 * 60 + 31) * 60_000L
        val at0900 = localDayStart + (9 * 60) * 60_000L
        assertFalse(Scheduler.isDue(b, at0831, at0900, tr))
        // ertesi gün aynı saat tekrar çalışır
        assertTrue(Scheduler.isDue(b, at0831, at0900 + Scheduler.DAY_MS, tr))
    }

    @Test
    fun `sonraki calisma zamani hesaplanir`() {
        val b = bot(Schedule(ScheduleMode.INTERVAL, everyMinutes = 30, prompt = "x"))
        val now = 5_000_000L
        assertEquals(now, Scheduler.nextRun(b, null, now, tr))
        assertEquals(now + 30 * 60_000L, Scheduler.nextRun(b, now, now, tr))
        val off = bot(Schedule(ScheduleMode.OFF))
        assertEquals(null, Scheduler.nextRun(off, null, now, tr))
    }

    @Test
    fun `hazir botlardan yalnizca zamani gelenler secilir`() {
        val now = 100_000_000L
        val a = BotSpec(id = "a", name = "A", schedule = Schedule(ScheduleMode.INTERVAL, everyMinutes = 10, prompt = "x"))
        val c = BotSpec(id = "c", name = "C", schedule = Schedule(ScheduleMode.OFF))
        val due = Scheduler.dueBots(listOf(a, c), mapOf("a" to now - 5 * 60_000L), now, tr)
        assertTrue(due.isEmpty())
        val due2 = Scheduler.dueBots(listOf(a, c), mapOf("a" to now - 11 * 60_000L), now, tr)
        assertEquals(listOf("a"), due2.map { it.id })
    }

    @Test
    fun `tick araligi makul sinirlarda kalir`() {
        val hizli = BotSpec(id = "h", name = "H", schedule = Schedule(ScheduleMode.INTERVAL, everyMinutes = 1, prompt = "x"))
        val yavas = BotSpec(id = "y", name = "Y", schedule = Schedule(ScheduleMode.INTERVAL, everyMinutes = 600, prompt = "x"))
        assertEquals(60_000L, Scheduler.tickInterval(listOf(hizli)))
        assertEquals(15 * 60_000L, Scheduler.tickInterval(listOf(yavas)))
        assertEquals(5 * 60_000L, Scheduler.tickInterval(emptyList()))
    }
}
