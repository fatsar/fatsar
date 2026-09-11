package com.fatsar.hermes.core

import com.fatsar.hermes.core.backend.Backends
import com.fatsar.hermes.core.model.BotSpec
import com.fatsar.hermes.core.model.ChatMessage
import com.fatsar.hermes.core.model.RunEvent
import com.fatsar.hermes.core.model.Role
import com.fatsar.hermes.core.model.ServerKind
import com.fatsar.hermes.core.model.ServerProfile
import com.fatsar.hermes.core.net.HttpTransport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class BackendTest {

    private val server = MockWebServer()
    private val transport = HttpTransport()

    private fun profile(kind: ServerKind, token: String = "test-token") = ServerProfile(
        id = "s1",
        name = "Test",
        kind = kind,
        baseUrl = server.url("/").toString(),
        token = token,
    )

    private val bot = BotSpec(
        id = "b1",
        name = "Test Botu",
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
    fun `openai akisi metin parcalarina cevrilir`() = runTest {
        server.enqueue(
            sse(
                """
                data: {"choices":[{"delta":{"role":"assistant"}}]}

                data: {"choices":[{"delta":{"content":"Mer"}}]}

                data: {"choices":[{"delta":{"content":"haba!"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3}}

                data: [DONE]

                """.trimIndent() + "\n",
            ),
        )
        val backend = Backends.create(profile(ServerKind.OPENAI), transport)
        val events = backend.run(bot, emptyList(), "Selam").toList()

        val text = events.filterIsInstance<RunEvent.Delta>().joinToString("") { it.text }
        assertEquals("Merhaba!", text)
        assertTrue(events.any { it is RunEvent.Started })
        assertTrue(events.any { it is RunEvent.Usage && it.promptTokens == 12 })
        assertEquals("Merhaba!", events.filterIsInstance<RunEvent.Message>().first().content)
        assertTrue(events.last() is RunEvent.Finished)

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val sent = request.body.readUtf8()
        assertTrue(sent.contains("\"model\":\"grok-3\""))
        assertTrue(sent.contains("\"stream\":true"))
        assertTrue(sent.contains("Sen bir asistansın"))
        assertTrue(sent.contains("Selam"))
    }

    @Test
    fun `openai hata yaniti anlasilir mesaja donusur`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )
        val backend = Backends.create(profile(ServerKind.OPENAI), transport)
        val events = backend.run(bot, emptyList(), "Selam").toList()
        val failed = events.filterIsInstance<RunEvent.Failed>().first()
        assertTrue(failed.message.contains("401"))
        assertTrue(failed.message.contains("Incorrect API key"))
        assertTrue(events.last() is RunEvent.Finished)
    }

    @Test
    fun `model secilmemisse anlasilir uyari doner`() = runTest {
        val backend = Backends.create(profile(ServerKind.OPENAI), transport)
        val events = backend.run(bot.copy(model = ""), emptyList(), "Selam").toList()
        assertTrue(events.filterIsInstance<RunEvent.Failed>().first().message.contains("model adı boş"))
    }

    @Test
    fun `openai model listesi okunur`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"grok-3"},{"id":"grok-2"}]}"""))
        val backend = Backends.create(profile(ServerKind.OPENAI), transport)
        assertEquals(listOf("grok-2", "grok-3"), backend.models())
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun `ollama ndjson akisi okunur`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"message":{"role":"assistant","content":"Mer"},"done":false}
                {"message":{"role":"assistant","content":"haba"},"done":false}
                {"done":true,"prompt_eval_count":9,"eval_count":2}
                """.trimIndent() + "\n",
            ),
        )
        val backend = Backends.create(profile(ServerKind.OLLAMA, token = ""), transport)
        val events = backend.run(bot.copy(model = "llama3.1:8b"), emptyList(), "Selam").toList()
        assertEquals("Merhaba", events.filterIsInstance<RunEvent.Delta>().joinToString("") { it.text })
        assertEquals(9, events.filterIsInstance<RunEvent.Usage>().first().promptTokens)
        assertTrue(events.last() is RunEvent.Finished)
        assertEquals("/api/chat", server.takeRequest().path)
    }

    @Test
    fun `hermes agent olaylari eslenir`() = runTest {
        server.enqueue(
            sse(
                """
                event: run.started
                data: {"run_id":"r_1","bot_id":"b1"}

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
        val backend = Backends.create(profile(ServerKind.HERMES), transport)
        val history = listOf(ChatMessage(id = "m1", role = Role.USER, content = "eski soru"))
        val events = backend.run(bot.copy(tools = listOf("shell")), history, "Disk durumu?").toList()

        assertEquals("r_1", events.filterIsInstance<RunEvent.Started>().first().runId)
        val call = events.filterIsInstance<RunEvent.ToolCall>().first()
        assertEquals("shell", call.name)
        assertTrue(call.args.contains("df -h"))
        assertTrue(events.filterIsInstance<RunEvent.ToolResult>().first().ok)
        assertEquals("Disk iyi durumda.", events.filterIsInstance<RunEvent.Delta>().joinToString("") { it.text })
        assertEquals("Disk iyi durumda.", events.filterIsInstance<RunEvent.Message>().first().content)
        val finished = events.filterIsInstance<RunEvent.Finished>().first()
        assertEquals("ok", finished.status)
        assertEquals(1500L, finished.durationMs)

        val request = server.takeRequest()
        assertEquals("/v1/bots/b1/runs", request.path)
        val sent = request.body.readUtf8()
        assertTrue(sent.contains("\"input\":\"Disk durumu?\""))
        assertTrue(sent.contains("\"bot\":"))
        assertTrue(sent.contains("eski soru"))
        assertTrue(sent.contains("\"tools\":[\"shell\"]"))
    }

    @Test
    fun `hermes saglik bilgisi okunur`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"name":"hermes-agent","version":"1.0.0","uptime_s":120,"host":"vps-1","bots":2,
                    "backends":["openai","ollama","echo"],"tools":["shell","http_get"]}""",
            ),
        )
        val backend = Backends.create(profile(ServerKind.HERMES), transport)
        val info = backend.health()
        assertEquals("hermes-agent", info.name)
        assertEquals(2, info.bots)
        assertTrue(info.tools.contains("shell"))
        assertEquals("/v1/health", server.takeRequest().path)
    }

    @Test
    fun `hermes bot listesi ve kaydi calisir`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"bots":[{"id":"b9","name":"Uzak Bot","model":"grok-3","system_prompt":"selam",
                    "schedule":{"mode":"INTERVAL","every_minutes":15,"prompt":"kontrol"}}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val backend = Backends.create(profile(ServerKind.HERMES), transport)

        val bots = backend.listBots()
        assertEquals(1, bots.size)
        assertEquals("Uzak Bot", bots[0].name)
        assertEquals(15, bots[0].schedule.everyMinutes)
        assertEquals("/v1/bots", server.takeRequest().path)

        backend.pushBot(bot)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/v1/bots/b1", put.path)
        assertTrue(put.body.readUtf8().contains("\"name\":\"Test Botu\""))
    }
}
