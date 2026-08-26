package com.fatsar.notlar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RelativeTimeTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    /** 2024-05-15 12:00:00 UTC */
    private val now = 1_715_774_400_000L

    @Test
    fun `bir dakikadan yeni az once`() {
        assertEquals(RelativeTime.Label.JustNow, RelativeTime.describe(now, now - 30_000, utc))
    }

    @Test
    fun `dakikalar`() {
        assertEquals(RelativeTime.Label.Minutes(5), RelativeTime.describe(now, now - 5 * 60_000, utc))
        assertEquals(RelativeTime.Label.Minutes(59), RelativeTime.describe(now, now - 59 * 60_000, utc))
    }

    @Test
    fun `ayni gun icinde saatler`() {
        assertEquals(RelativeTime.Label.Hours(3), RelativeTime.describe(now, now - 3 * 3_600_000, utc))
    }

    @Test
    fun `dun`() {
        val label = RelativeTime.describe(now, now - day, utc)
        assertTrue(label is RelativeTime.Label.Yesterday)
    }

    @Test
    fun `bir haftadan yeni gun sayisi`() {
        assertEquals(RelativeTime.Label.DaysAgo(3), RelativeTime.describe(now, now - 3 * day, utc))
    }

    @Test
    fun `bir haftadan eski tam tarih`() {
        val label = RelativeTime.describe(now, now - 30 * day, utc)
        assertTrue(label is RelativeTime.Label.OnDate)
    }

    @Test
    fun `gece yarisini gecince takvim gunu esas alinir`() {
        // 00:10'da, 40 dakika onceki (dun 23 50) not "dakika" olarak gosterilir
        val afterMidnight = now + 12 * 3_600_000 + 10 * 60_000
        assertEquals(
            RelativeTime.Label.Minutes(40),
            RelativeTime.describe(afterMidnight, afterMidnight - 40 * 60_000, utc)
        )
        // Ama 3 saat oncesi (dun 21 10) artik "dun"
        val label = RelativeTime.describe(afterMidnight, afterMidnight - 3 * 3_600_000, utc)
        assertTrue(label is RelativeTime.Label.Yesterday)
    }
}
