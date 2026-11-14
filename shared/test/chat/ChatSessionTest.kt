package raju.shingadiya.juniper.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.db.SqliteDatabase
import raju.shingadiya.juniper.ollama.OllamaClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSessionTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val sentBodies = mutableListOf<String>()
    private lateinit var repository: SqliteChatRepository
    private lateinit var client: OllamaClient

    private suspend fun session(
        handler: (String) -> MockRequestHandleScope.() -> HttpResponseData,
        systemPrompt: String? = null,
    ): ChatSession {
        val engine = MockEngine { request ->
            sentBodies += (request.body as TextContent).text
            handler(sentBodies.last()).invoke(this)
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(OllamaClient.json) } }
        client = OllamaClient(baseUrl = "http://test", httpClient = http)
        repository = SqliteChatRepository(SqliteDatabase.open("jdbc:sqlite::memory:"))
        val chat = repository.createChat(model = "qwen2.5:7b")
        return ChatSession(
            client = client,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            chatId = chat.id,
            model = "qwen2.5:7b",
            systemPrompt = systemPrompt,
        )
    }

    private fun streamOf(vararg fragments: String): MockRequestHandleScope.() -> HttpResponseData {
        val lines = fragments.map { """{"model":"m","message":{"role":"assistant","content":"$it"},"done":false}""" } +
            """{"model":"m","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}"""
        return {
            respond(content = ByteReadChannel(lines.joinToString("\n").encodeToByteArray()), headers = jsonHeaders)
        }
    }

    @Test
    fun `send streams the reply into the last assistant bubble`() = runTest {
        val session = session(handler = { streamOf("Hel", "lo") })

        session.send("hi")

        val messages = session.messages.value
        assertEquals(2, messages.size)
        assertEquals(ChatBubble("user", "hi"), messages[0])
        assertEquals(ChatBubble("assistant", "Hello"), messages[1])
        assertFalse(session.isSending.value)
    }

    @Test
    fun `request includes system prompt and the new user message`() = runTest {
        val session = session(handler = { streamOf("ok") }, systemPrompt = "Be brief.")

        session.send("first question")

        val body = sentBodies.single()
        assertTrue(body.contains("\"role\":\"system\""), body)
        assertTrue(body.contains("Be brief."), body)
        assertTrue(body.contains("\"content\":\"first question\""), body)
        assertTrue(body.contains("\"stream\":true"), body)
    }

    @Test
    fun `second send includes earlier exchange as history`() = runTest {
        val session = session(handler = { streamOf("answer") })

        session.send("one")
        session.send("two")

        val second = sentBodies[1]
        assertTrue(second.contains("\"content\":\"one\""), second)
        assertTrue(second.contains("\"content\":\"answer\""), second)
        assertTrue(second.contains("\"content\":\"two\""), second)
        assertEquals(4, session.messages.value.size)
    }

    @Test
    fun `blank input is ignored and sends no request`() = runTest {
        val session = session(handler = { streamOf("x") })

        session.send("   ")

        assertTrue(sentBodies.isEmpty())
        assertTrue(session.messages.value.isEmpty())
    }

    @Test
    fun `failed request shows an error bubble and keeps the user message`() = runTest {
        val session = session(handler = {
            { respondError(HttpStatusCode.InternalServerError, "model crashed") }
        })

        session.send("hello")

        val messages = session.messages.value
        assertEquals(2, messages.size)
        assertTrue(messages[1].isError)
        assertTrue(messages[1].content.contains("model crashed"))
        assertFalse(session.isSending.value)
    }

    @Test
    fun `error bubbles are not sent back as history`() = runTest {
        var attempt = 0
        val session = session(handler = {
            attempt++
            if (attempt == 1) {
                { respondError(HttpStatusCode.InternalServerError, "boom") }
            } else {
                streamOf("fine")
            }
        })

        session.send("first")
        session.send("second")

        val second = sentBodies[1]
        assertFalse(second.contains("boom"), second)
        assertTrue(second.contains("\"content\":\"first\""), second)
    }

    @Test
    fun `clear removes all messages from memory and storage`() = runTest {
        val session = session(handler = { streamOf("x") })
        session.send("hi")

        session.clear()

        assertTrue(session.messages.value.isEmpty())
        assertTrue(repository.loadMessages(session.chatId).isEmpty())
    }

    @Test
    fun `replies are saved to the repository`() = runTest {
        val session = session(handler = { streamOf("Hel", "lo") })

        session.send("hi")

        assertEquals(session.messages.value, repository.loadMessages(session.chatId))
    }

    @Test
    fun `error replies are saved with the error flag`() = runTest {
        val session = session(handler = { { respondError(HttpStatusCode.InternalServerError, "model crashed") } })

        session.send("hello")

        val saved = repository.loadMessages(session.chatId)
        assertEquals(2, saved.size)
        assertTrue(saved[1].isError)
    }

    @Test
    fun `first message becomes the chat title`() = runTest {
        val session = session(handler = { streamOf("ok") })

        session.send("  Explain coroutines in Kotlin  ")

        assertEquals("Explain coroutines in Kotlin", repository.chats.value.single().title)
    }

    @Test
    fun `long first message is shortened for the title`() = runTest {
        val session = session(handler = { streamOf("ok") })

        session.send("a".repeat(100))

        val title = repository.chats.value.single().title
        assertTrue(title.length <= 41, title)
        assertTrue(title.endsWith("…"), title)
    }

    @Test
    fun `a reopened session loads the saved history`() = runTest {
        val session = session(handler = { streamOf("saved reply") })
        session.send("remember me")

        val reopened = ChatSession(
            client = client,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            chatId = session.chatId,
            model = session.model,
            initialMessages = repository.loadMessages(session.chatId),
        )

        assertEquals(
            listOf(ChatBubble("user", "remember me"), ChatBubble("assistant", "saved reply")),
            reopened.messages.value,
        )
    }
}
