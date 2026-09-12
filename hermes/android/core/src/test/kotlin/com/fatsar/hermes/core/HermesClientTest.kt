package com.fatsar.hermes.core

import com.fatsar.hermes.core.backend.HermesClient
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.Role
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HttpTransport
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class HermesClientTest {

    private val server = MockWebServer()

    private val client: HermesClient
        get() = HermesClient(
            ServerProfile(id = "s1", name = "VPS", baseUrl = server.url("/").toString(), token = "test-token"),
            HttpTransport(),
        )

    private val bot = BotSpec(
        id = "b1",
        name = "Test Botu",
        backend = "xai",
        model = "grok-3",
        systemPrompt = "Sen bir asistansın",
        temperature = 0.5,
        maxTokens = 256,
    )

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun sse(body: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(body)

    @Test
    fun `agent bilgisi ve hazir saglayicilar okunur`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"name":"hermes-agent","version":"1.0.0","uptime_s":120,"host":"vps-1","bots":2,
                    "backends":["anthropic","echo","ollama","openai","xai"],
                    "backends_ready":["echo","ollama","xai"],
                    "default_backend":"xai","shell_enabled":true,
                    "tools":["shell","http_get"]}""",
            ),
        )
        val info = client.health()
        assertEquals("hermes-agent", info.name)
        assertEquals(2, info.bots)
        assertEquals("xai", info.defaultBackend)
        assertTrue(info.shellEnabled)
        assertEquals(listOf("echo", "ollama", "xai"), info.backendsReady)
        assertEquals("/v1/health", server.takeRequest().path)
    }

    @Test
    fun `agentta tanimli LLM saglayicilari listelenir`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"default_backend":"xai","backends":[
                    {"name":"xai","ready":true,"is_default":true,"default_model":"grok-3",
                     "base_url":"https://api.x.ai/v1","supports_tools":true,"note":""},
                    {"name":"openai","ready":false,"is_default":false,"default_model":"gpt-4o-mini",
                     "note":"API anahtarı ayarlı değil"},
                    {"name":"anthropic","ready":true,"supports_tools":false,"default_model":"claude-sonnet-4-5"}
                ]}""",
            ),
        )
        val backends = client.backends()
        assertEquals(3, backends.size)

        val xai = backends.first { it.name == "xai" }
        assertTrue(xai.ready)
        assertTrue(xai.isDefault)
        assertEquals("grok-3", xai.defaultModel)
        assertEquals("xai (grok-3)", xai.label)

        val openai = backends.first { it.name == "openai" }
        assertFalse(openai.ready)
        assertTrue(openai.note.contains("anahtar"))

        // Araç desteği olmayan sağlayıcı işaretlenir
        assertFalse(backends.first { it.name == "anthropic" }.supportsTools)
        assertEquals("/v1/backends", server.takeRequest().path)
    }

    @Test
    fun `secilen saglayicinin modelleri istenir`() = runTest {
        server.enqueue(MockResponse().setBody("""{"backend":"xai","models":["grok-3","grok-2"]}"""))
        assertEquals(listOf("grok-3", "grok-2"), client.models("xai"))
        assertEquals("/v1/models?backend=xai", server.takeRequest().path)
    }

    @Test
    fun `saglayici adi kodlanarak gonderilir`() = runTest {
        server.enqueue(MockResponse().setBody("""{"models":[]}"""))
        client.models("yerel sunucu")
        assertEquals("/v1/models?backend=yerel+sunucu", server.takeRequest().path)
    }

    @Test
    fun `calistirma olaylari eslenir`() = runTest {
        server.enqueue(
            sse(
                """
                event: run.started
                data: {"run_id":"r_1","bot_id":"b1","backend":"xai","model":"grok-3"}

                event: tool.call
                data: {"name":"shell","args":{"command":"df -h"},"call_id":"c1"}

                event: tool.result
                data: {"call_id":"c1","name":"shell","ok":true,"result":"/dev/vda 40% kullanımda"}

                event: token
                data: {"text":"Disk "}

                event: token
                data: {"text":"iyi durumda."}

                event: message
                data: {"content":"Disk iyi durumda."}

                event: usage
                data: {"prompt_tokens":40,"completion_tokens":6}

                event: run.finished
                data: {"run_id":"r_1","status":"ok","duration_ms":1500}

                """.trimIndent() + "\n",
            ),
        )
        val history = listOf(ChatMessage(id = "m1", role = Role.USER, content = "eski soru"))
        val events = client.run(bot.copy(tools = listOf("shell")), history, "Disk durumu?").toList()

        assertEquals("r_1", events.filterIsInstance<RunEvent.Started>().first().runId)
        val call = events.filterIsInstance<RunEvent.ToolCall>().first()
        assertEquals("shell", call.name)
        assertTrue(call.args.contains("df -h"))
        assertTrue(events.filterIsInstance<RunEvent.ToolResult>().first().ok)
        assertEquals("Disk iyi durumda.", events.filterIsInstance<RunEvent.Delta>().joinToString("") { it.text })
        assertEquals(40, events.filterIsInstance<RunEvent.Usage>().first().promptTokens)
        assertEquals(1500L, events.filterIsInstance<RunEvent.Finished>().first().durationMs)

        val request = server.takeRequest()
        assertEquals("/v1/bots/b1/runs", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val sent = request.body.readUtf8()
        assertTrue(sent.contains("\"input\":\"Disk durumu?\""))
        assertTrue(sent.contains("\"backend\":\"xai\""))
        assertTrue(sent.contains("eski soru"))
        assertTrue(sent.contains("\"tools\":[\"shell\"]"))
    }

    @Test
    fun `sunucu hatasi anlasilir mesaja donusur`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Token hatalı"}}"""))
        val events = client.run(bot, emptyList(), "Selam").toList()
        val failed = events.filterIsInstance<RunEvent.Failed>().first()
        assertTrue(failed.message.contains("401"))
        assertTrue(failed.message.contains("Token hatalı"))
        assertTrue(events.last() is RunEvent.Finished)
    }

    @Test
    fun `bot listesi ve kaydi calisir`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"bots":[{"id":"b9","name":"Uzak Bot","backend":"ollama","model":"llama3.1:8b",
                    "schedule":{"mode":"INTERVAL","every_minutes":15,"prompt":"kontrol"}}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val bots = client.listBots()
        assertEquals(1, bots.size)
        assertEquals("Uzak Bot", bots[0].name)
        assertEquals("ollama", bots[0].backend)
        assertEquals(15, bots[0].schedule.everyMinutes)
        assertEquals("/v1/bots", server.takeRequest().path)

        client.pushBot(bot)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/v1/bots/b1", put.path)
        assertTrue(put.body.readUtf8().contains("\"name\":\"Test Botu\""))
    }

    @Test
    fun `calisma gecmisi okunur`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"runs":[{"run_id":"r_9","bot_id":"b1","status":"ok","started_at":1.5,
                    "duration_ms":900,"preview":"Sorun yok"}]}""",
            ),
        )
        val runs = client.runHistory("b1", limit = 5)
        assertEquals(1, runs.size)
        assertEquals("r_9", runs[0].runId)
        assertEquals("Sorun yok", runs[0].preview)
        assertEquals("/v1/bots/b1/runs?limit=5", server.takeRequest().path)
    }
}

class HermesClientRunDetailTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `zamanlanmis calismanin tam ciktisi alinir`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"run":{"run_id":"r_5","bot_id":"b1","bot_name":"Haber Botu","status":"ok",
                    "trigger":"schedule","backend":"xai","model":"grok-3",
                    "input":"Bugünün haberlerini özetle","output":"1. Birinci haber…",
                    "duration_ms":4200}}""",
            ),
        )
        val client = HermesClient(
            ServerProfile(id = "s1", name = "VPS", baseUrl = server.url("/").toString(), token = "t"),
            HttpTransport(),
        )
        val detail = client.runDetail("r_5")!!
        assertEquals("Haber Botu", detail.botName)
        assertEquals("schedule", detail.trigger)
        assertEquals("1. Birinci haber…", detail.output)
        assertEquals("/v1/runs/r_5", server.takeRequest().path)
    }

    @Test
    fun `gecmiste zamanlanmis calismalar ayirt edilir`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"runs":[{"run_id":"r_1","status":"ok","trigger":"schedule","preview":"özet"},
                           {"run_id":"r_2","status":"ok","trigger":"manual","preview":"sohbet"}]}""",
            ),
        )
        val client = HermesClient(
            ServerProfile(id = "s1", name = "VPS", baseUrl = server.url("/").toString(), token = "t"),
            HttpTransport(),
        )
        val runs = client.runHistory("b1")
        assertTrue(runs[0].isScheduled)
        assertFalse(runs[1].isScheduled)
    }
}
