package com.fatsar.hermes.core

import com.fatsar.hermes.core.logic.Urls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlsTest {

    @Test
    fun `sema yoksa ip icin http eklenir`() {
        assertEquals("http://192.168.1.50:8713", Urls.normalizeBase("192.168.1.50:8713"))
        assertEquals("http://10.0.0.4", Urls.normalizeBase("10.0.0.4"))
    }

    @Test
    fun `alan adi icin https varsayilir`() {
        assertEquals("https://bot.ornek.com", Urls.normalizeBase("bot.ornek.com"))
    }

    @Test
    fun `sondaki bolu isaretleri atilir`() {
        assertEquals("https://api.x.ai/v1", Urls.normalizeBase("https://api.x.ai/v1//"))
        assertEquals("http://1.2.3.4:8713", Urls.normalizeBase("  http://1.2.3.4:8713/ "))
    }

    @Test
    fun `openai sohbet adresi dogru kurulur`() {
        assertEquals("https://api.x.ai/v1/chat/completions", Urls.openAiChatUrl("https://api.x.ai/v1"))
        assertEquals("https://api.openai.com/v1/chat/completions", Urls.openAiChatUrl("https://api.openai.com"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", Urls.openAiChatUrl("https://openrouter.ai/api/v1"))
        assertEquals("http://1.2.3.4:8000/v1/chat/completions", Urls.openAiChatUrl("1.2.3.4:8000"))
        assertEquals(
            "https://x.com/v1/chat/completions",
            Urls.openAiChatUrl("https://x.com/v1/chat/completions"),
        )
    }

    @Test
    fun `openai model adresi dogru kurulur`() {
        assertEquals("https://api.x.ai/v1/models", Urls.openAiModelsUrl("https://api.x.ai/v1"))
        assertEquals("https://api.x.ai/v1/models", Urls.openAiModelsUrl("https://api.x.ai/v1/chat/completions"))
        assertEquals("http://localhost:1234/v1/models", Urls.openAiModelsUrl("http://localhost:1234"))
    }

    @Test
    fun `yerel adresler tanimlanir`() {
        assertTrue(Urls.isLocalAddress("http://192.168.1.5:8713"))
        assertTrue(Urls.isLocalAddress("http://10.8.0.2"))
        assertTrue(Urls.isLocalAddress("http://localhost:8713"))
        assertTrue(Urls.isLocalAddress("http://172.16.0.9"))
        assertFalse(Urls.isLocalAddress("http://89.12.4.7"))
        assertFalse(Urls.isLocalAddress("https://api.x.ai"))
    }

    @Test
    fun `internete acik http uyarisi verilir`() {
        assertTrue(Urls.isInsecurePublic("http://89.12.4.7:8713"))
        assertFalse(Urls.isInsecurePublic("https://bot.ornek.com"))
        assertFalse(Urls.isInsecurePublic("http://192.168.1.5:8713"))
    }

    @Test
    fun `ollama adresi eklenir`() {
        assertEquals("http://1.2.3.4:11434/api/tags", Urls.ollamaUrl("1.2.3.4:11434", "/api/tags"))
    }
}

class PairingTest {

    @Test
    fun `agent kodu cozulur`() {
        // hermes_agent.py --show-pairing çıktısının birebir aynısı
        val code = "HERMES1:eyJ1IjoiaHR0cDovLzE5Mi4wLjIuMjo4NzEzIiwidCI6ImRlbW8tdG9rZW4tMSIsIm4iOiJ2bSJ9"
        val payload = com.fatsar.hermes.core.logic.Pairing.decode(code)!!
        assertEquals("http://192.0.2.2:8713", payload.url)
        assertEquals("demo-token-1", payload.token)
        assertEquals("vm", payload.name)
    }

    @Test
    fun `kod bosluk ve satir sonlarina toleransli`() {
        val code = com.fatsar.hermes.core.logic.Pairing.encode("http://1.2.3.4:8713", "abc", "VPS")
        val kirli = "  Eşleştirme kodu:\n$code \n"
        val payload = com.fatsar.hermes.core.logic.Pairing.decode(kirli)!!
        assertEquals("http://1.2.3.4:8713", payload.url)
        assertEquals("abc", payload.token)
    }

    @Test
    fun `gecersiz kod null doner`() {
        assertEquals(null, com.fatsar.hermes.core.logic.Pairing.decode("merhaba"))
        assertEquals(null, com.fatsar.hermes.core.logic.Pairing.decode("HERMES1:!!!!"))
        assertEquals(null, com.fatsar.hermes.core.logic.Pairing.decode(""))
    }

    @Test
    fun `koddan sunucu profili uretilir`() {
        val code = com.fatsar.hermes.core.logic.Pairing.encode("1.2.3.4:8713", "tok", "")
        val payload = com.fatsar.hermes.core.logic.Pairing.decode(code)!!
        val profile = com.fatsar.hermes.core.logic.Pairing.toProfile(payload, "s1", 100L)
        assertEquals("http://1.2.3.4:8713", profile.baseUrl)
        assertEquals("tok", profile.token)
        assertEquals("1.2.3.4", profile.name)
        assertEquals(com.fatsar.hermes.core.model.ServerKind.HERMES, profile.kind)
    }
}
