package com.fatsar.hermes.core

import com.fatsar.hermes.core.logic.Prompts
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptsTest {

    private fun bot(memory: Int = 12, system: String = "Sen bir asistansın.") =
        BotSpec(id = "b1", name = "Test", systemPrompt = system, memoryTurns = memory)

    private fun msg(role: Role, content: String, error: Boolean = false, streaming: Boolean = false) =
        ChatMessage(id = content, role = role, content = content, error = error, streaming = streaming)

    @Test
    fun `sistem istemi en basa gelir ve girdi sona eklenir`() {
        val out = Prompts.build(bot(), emptyList(), "Merhaba")
        assertEquals(2, out.size)
        assertEquals("system", out[0].role)
        assertEquals("Sen bir asistansın.", out[0].content)
        assertEquals("user", out[1].role)
        assertEquals("Merhaba", out[1].content)
    }

    @Test
    fun `sistem istemi bossa eklenmez`() {
        val out = Prompts.build(bot(system = "   "), emptyList(), "Selam")
        assertEquals(1, out.size)
        assertEquals("user", out[0].role)
    }

    @Test
    fun `hafiza siniri son turlari birakir`() {
        val history = (1..10).flatMap {
            listOf(msg(Role.USER, "soru$it"), msg(Role.ASSISTANT, "cevap$it"))
        }
        val out = Prompts.build(bot(memory = 2), history, "yeni")
        // sistem + 2 tur (4 mesaj) + yeni girdi
        assertEquals(6, out.size)
        assertEquals("soru9", out[1].content)
        assertEquals("cevap10", out[4].content)
        assertEquals("yeni", out[5].content)
    }

    @Test
    fun `hafiza sifirsa gecmis gonderilmez`() {
        val history = listOf(msg(Role.USER, "eski"), msg(Role.ASSISTANT, "yanit"))
        val out = Prompts.build(bot(memory = 0), history, "yeni")
        assertEquals(2, out.size)
        assertEquals("yeni", out[1].content)
    }

    @Test
    fun `arac hatali ve akan mesajlar gecmise girmez`() {
        val history = listOf(
            msg(Role.USER, "soru"),
            msg(Role.TOOL, "arac ciktisi"),
            msg(Role.ASSISTANT, "hatali", error = true),
            msg(Role.ASSISTANT, "akan", streaming = true),
            msg(Role.ASSISTANT, "gecerli"),
        )
        val out = Prompts.history(bot(), history)
        assertEquals(2, out.size)
        assertEquals(listOf("soru", "gecerli"), out.map { it.content })
    }

    @Test
    fun `onizleme kisaltir ve bosluklari temizler`() {
        assertEquals("bir iki", Prompts.preview("  bir\n\n iki  "))
        val long = "a".repeat(200)
        val p = Prompts.preview(long, 20)
        assertEquals(20, p.length)
        assertTrue(p.endsWith("…"))
    }
}
