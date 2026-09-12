package com.fatsar.hermes.core

import com.fatsar.hermes.core.logic.Ids
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.Role
import com.fatsar.hermes.core.model.Schedule
import com.fatsar.hermes.core.model.ScheduleMode
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.store.AppData
import com.fatsar.hermes.core.store.MemoryStorage
import com.fatsar.hermes.core.store.Repository
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryTest {

    private fun sampleData() = AppData(
        servers = listOf(
            ServerProfile(id = "s1", name = "VPS", baseUrl = "http://1.2.3.4:8713", token = "gizli"),
        ),
        bots = listOf(
            BotSpec(
                id = "b1",
                name = "Bekçi",
                serverId = "s1",
                model = "grok-3",
                systemPrompt = "Sen bir bekçisin",
                tools = listOf("shell"),
                backend = "xai",
                schedule = Schedule(ScheduleMode.INTERVAL, everyMinutes = 30, prompt = "kontrol"),
            ),
        ),
        activeBotId = "b1",
        seenRuns = mapOf("b1" to "run_abc"),
    )

    @Test
    fun `kaydet ve yukle ayni veriyi verir`() {
        val repo = Repository(MemoryStorage())
        val data = sampleData()
        repo.save(data)
        assertEquals(data, repo.load())
    }

    @Test
    fun `bos depoda varsayilan durum doner`() {
        val repo = Repository(MemoryStorage())
        assertEquals(AppData(), repo.load())
    }

    @Test
    fun `bozuk JSON uygulamayi cokertmez`() {
        val storage = MemoryStorage(mapOf(Repository.STATE_FILE to "{bu gecerli json degil"))
        assertEquals(AppData(), Repository(storage).load())
    }

    @Test
    fun `sohbet gecmisi eklenir ve okunur`() {
        val repo = Repository(MemoryStorage())
        repo.appendMessage("b1", ChatMessage(id = "m1", role = Role.USER, content = "selam"))
        repo.appendMessage("b1", ChatMessage(id = "m2", role = Role.ASSISTANT, content = "merhaba"))
        val msgs = repo.transcript("b1")
        assertEquals(2, msgs.size)
        assertEquals(Role.ASSISTANT, msgs[1].role)
        assertEquals("merhaba", msgs[1].content)
    }

    @Test
    fun `gecmis ust sinirda budanir`() {
        val repo = Repository(MemoryStorage())
        val many = (1..Repository.MAX_MESSAGES_PER_BOT + 50).map {
            ChatMessage(id = "m$it", role = Role.USER, content = "mesaj$it")
        }
        repo.saveTranscript("b1", many)
        val loaded = repo.transcript("b1")
        assertEquals(Repository.MAX_MESSAGES_PER_BOT, loaded.size)
        assertEquals("mesaj51", loaded.first().content)
    }

    @Test
    fun `bot silinince gecmisi de silinir`() {
        val repo = Repository(MemoryStorage())
        repo.appendMessage("b1", ChatMessage(id = "m1", role = Role.USER, content = "selam"))
        repo.deleteBotData("b1")
        assertTrue(repo.transcript("b1").isEmpty())
    }

    @Test
    fun `disa ve ice aktarma calisir`() {
        val repo = Repository(MemoryStorage())
        repo.save(sampleData())
        val json = repo.exportJson()
        val repo2 = Repository(MemoryStorage())
        assertTrue(repo2.importJson(json))
        assertEquals(sampleData(), repo2.load())
        assertFalse(repo2.importJson("bozuk"))
        assertEquals(sampleData(), repo2.load())
    }

    @Test
    fun `kimlikler benzersiz ve maske calisir`() {
        val rnd = Random(42)
        val a = Ids.newId("bot", 1000, rnd)
        val b = Ids.newId("bot", 1000, rnd)
        assertTrue(a.startsWith("bot_"))
        assertFalse(a == b)
        assertEquals("xai-…cdef", Ids.mask("xai-1234567890abcdef"))
        assertEquals("", Ids.mask(""))
        assertEquals("••••", Ids.mask("abcd"))
    }
}
