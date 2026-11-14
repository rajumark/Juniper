package raju.shingadiya.juniper.ollama

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OllamaClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientFor(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): OllamaClient {
        val engine = MockEngine { request -> handler(request) }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(OllamaClient.json) }
        }
        return OllamaClient(baseUrl = "http://test", httpClient = httpClient)
    }

    private val request = ChatRequest(
        model = "qwen2.5:7b",
        messages = listOf(ChatMessage(role = "user", content = "hi")),
    )

    @Test
    fun `chat posts to api chat with stream disabled and parses the reply`() = runTest {
        var sentBody = ""
        val client = clientFor { req ->
            assertEquals("/api/chat", req.url.encodedPath)
            sentBody = (req.body as TextContent).text
            respond(
                content = """{"model":"qwen2.5:7b","message":{"role":"assistant","content":"hello"},"done":true,"done_reason":"stop","total_duration":123}""",
                headers = jsonHeaders,
            )
        }

        val reply = client.chat(request)

        assertTrue(sentBody.contains("\"stream\":false"), "request body was: $sentBody")
        assertEquals("hello", reply.message.content)
        assertEquals("assistant", reply.message.role)
        assertTrue(reply.done)
        assertEquals("stop", reply.doneReason)
    }

    @Test
    fun `chat omits null options and keeps format when set`() = runTest {
        var sentBody = ""
        val client = clientFor { req ->
            sentBody = (req.body as TextContent).text
            respond(
                content = """{"model":"m","message":{"role":"assistant","content":"{}"},"done":true}""",
                headers = jsonHeaders,
            )
        }

        client.chat(request.copy(format = JsonPrimitive("json")))

        assertFalse(sentBody.contains("\"options\""), "options should be omitted: $sentBody")
        assertTrue(sentBody.contains("\"format\":\"json\""), "format missing: $sentBody")
    }

    @Test
    fun `streamChat emits every chunk until done`() = runTest {
        val ndjson = listOf(
            """{"model":"m","message":{"role":"assistant","content":"Hel"},"done":false}""",
            """{"model":"m","message":{"role":"assistant","content":"lo"},"done":false}""",
            """{"model":"m","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}""",
            """{"model":"m","message":{"role":"assistant","content":"ignored"},"done":false}""",
        ).joinToString("\n")

        val client = clientFor {
            respond(
                content = ByteReadChannel(ndjson.encodeToByteArray()),
                headers = jsonHeaders,
            )
        }

        val chunks = client.streamChat(request).toList()

        assertEquals(listOf("Hel", "lo", ""), chunks.map { it.message.content })
        assertTrue(chunks.last().done)
        assertEquals("stop", chunks.last().doneReason)
    }

    @Test
    fun `listModels returns installed model names`() = runTest {
        val client = clientFor { req ->
            assertEquals("/api/tags", req.url.encodedPath)
            respond(
                content = """{"models":[{"name":"qwen2.5:7b","size":1},{"name":"llama3:8b"}]}""",
                headers = jsonHeaders,
            )
        }

        assertEquals(listOf("qwen2.5:7b", "llama3:8b"), client.listModels())
    }

    @Test
    fun `http error is reported as OllamaException with the server message`() = runTest {
        val client = clientFor {
            respondError(HttpStatusCode.NotFound, """{"error":"model 'x' not found"}""")
        }

        val error = assertFailsWith<OllamaException> { client.chat(request) }

        assertTrue(error.message!!.contains("404"))
        assertTrue(error.message!!.contains("model 'x' not found"))
    }

    @Test
    fun `unreachable server is reported as OllamaException`() = runTest {
        val client = clientFor {
            throw java.net.ConnectException("Connection refused")
        }

        val error = assertFailsWith<OllamaException> { client.chat(request) }

        assertTrue(error.message!!.contains("Is it running?"))
    }
}
