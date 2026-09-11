package com.fatsar.hermes.core

import com.fatsar.hermes.core.net.SseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseParserTest {

    private fun parseAll(raw: String): List<Pair<String, String>> {
        val p = SseParser()
        val out = ArrayList<Pair<String, String>>()
        raw.split("\n").forEach { line -> p.feed(line)?.let { out += it.event to it.data } }
        p.flush()?.let { out += it.event to it.data }
        return out
    }

    @Test
    fun `olay adi ve veri ayristirilir`() {
        val events = parseAll("event: token\ndata: {\"text\":\"Mer\"}\n\nevent: token\ndata: {\"text\":\"haba\"}\n\n")
        assertEquals(2, events.size)
        assertEquals("token", events[0].first)
        assertEquals("{\"text\":\"Mer\"}", events[0].second)
        assertEquals("{\"text\":\"haba\"}", events[1].second)
    }

    @Test
    fun `olay adi yoksa message kabul edilir`() {
        val events = parseAll("data: merhaba\n\n")
        assertEquals(1, events.size)
        assertEquals("message", events[0].first)
        assertEquals("merhaba", events[0].second)
    }

    @Test
    fun `cok satirli data birlestirilir`() {
        val events = parseAll("data: bir\ndata: iki\n\n")
        assertEquals(1, events.size)
        assertEquals("bir\niki", events[0].second)
    }

    @Test
    fun `yorum satirlari ve CRLF yok sayilir`() {
        val events = parseAll(": keep-alive\r\nevent: token\r\ndata: x\r\n\r\n")
        assertEquals(1, events.size)
        assertEquals("token", events[0].first)
        assertEquals("x", events[0].second)
    }

    @Test
    fun `bosluksuz data alani da okunur`() {
        val events = parseAll("data:{\"a\":1}\n\n")
        assertEquals("{\"a\":1}", events[0].second)
    }

    @Test
    fun `DONE isareti tanimlanir`() {
        val p = SseParser()
        p.feed("data: [DONE]")
        val ev = p.feed("")
        assertTrue(ev!!.isDone)
    }

    @Test
    fun `bos akista olay uretilmez`() {
        val p = SseParser()
        assertNull(p.feed(""))
        assertNull(p.flush())
    }

    @Test
    fun `akis sonunda yarim olay flush ile gelir`() {
        val p = SseParser()
        p.feed("event: message")
        p.feed("data: son")
        val ev = p.flush()
        assertEquals("message", ev!!.event)
        assertEquals("son", ev.data)
    }
}
