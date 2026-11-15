package raju.shingadiya.juniper.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.db.SqliteDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSessionTest {

    private lateinit var repository: SqliteChatRepository
    private lateinit var runner: FakeChatAgentRunner

    private suspend fun session(
        runner: FakeChatAgentRunner = FakeChatAgentRunner(),
        model: String = "qwen2.5:7b",
    ): ChatSession {
        this.runner = runner
        repository = SqliteChatRepository(SqliteDatabase.open("jdbc:sqlite::memory:"))
        val chat = repository.createChat(model = model)
        return ChatSession(
            runner = runner,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            chatId = chat.id,
            model = model,
            systemPrompt = "Be brief.",
        )
    }

    @Test
    fun `send shows the user message and the agent reply`() = runTest {
        val session = session()

        session.send("hi")

        assertEquals(
            listOf(ChatBubble("user", "hi"), ChatBubble("assistant", "answer to hi")),
            session.messages.value,
        )
        assertFalse(session.isSending.value)
    }

    @Test
    fun `tool calls appear as tool bubbles before the reply`() = runTest {
        val session = session(runner = FakeChatAgentRunner(toolCalls = listOf("get_android_version" to "15")))

        session.send("what version?")

        val messages = session.messages.value
        assertEquals(3, messages.size)
        assertEquals("get_android_version", messages[1].toolName)
        assertEquals("15", messages[1].content)
        assertTrue(messages[1].isTool)
        assertEquals("answer to what version?", messages[2].content)
    }

    @Test
    fun `everything from a reply is saved, in order`() = runTest {
        val session = session(runner = FakeChatAgentRunner(toolCalls = listOf("get_battery_info" to "{}")))

        session.send("battery?")

        assertEquals(session.messages.value, repository.loadMessages(session.chatId))
    }

    @Test
    fun `a failed run shows and saves an error bubble`() = runTest {
        val runner = FakeChatAgentRunner().apply { failure = IllegalStateException("model not found") }
        val session = session(runner = runner)

        session.send("hello")

        val saved = repository.loadMessages(session.chatId)
        assertEquals(2, saved.size)
        assertTrue(saved[1].isError)
        assertTrue(saved[1].content.contains("model not found"))
        assertFalse(session.isSending.value)
    }

    @Test
    fun `blank input and a missing model send nothing`() = runTest {
        val session = session()
        session.send("   ")
        assertTrue(session.messages.value.isEmpty())
        assertTrue(runner.inputs.isEmpty())

        val noModel = session(model = "")
        noModel.send("hello")
        assertTrue(noModel.messages.value.isEmpty())
    }

    @Test
    fun `the selected model is passed to the agent`() = runTest {
        val session = session()

        session.selectModel("llama3.2:3b")
        session.send("hi")

        assertEquals(listOf("llama3.2:3b"), runner.models)
    }

    @Test
    fun `first message becomes the chat title`() = runTest {
        val session = session()

        session.send("  Explain coroutines in Kotlin  ")

        assertEquals("Explain coroutines in Kotlin", repository.chats.value.single().title)
    }

    @Test
    fun `long first message is shortened for the title`() = runTest {
        val session = session()

        session.send("a".repeat(100))

        val title = repository.chats.value.single().title
        assertTrue(title.length <= 41, title)
        assertTrue(title.endsWith("…"), title)
    }

    @Test
    fun `clear removes messages from memory and storage and forgets the agent memory`() = runTest {
        val session = session()
        session.send("hi")

        session.clear()

        assertTrue(session.messages.value.isEmpty())
        assertTrue(repository.loadMessages(session.chatId).isEmpty())
        assertEquals(listOf(session.chatId), runner.forgotten)
    }

    @Test
    fun `a reopened session shows the saved history`() = runTest {
        val session = session()
        session.send("remember me")

        val reopened = ChatSession(
            runner = runner,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            chatId = session.chatId,
            model = session.model,
            initialMessages = repository.loadMessages(session.chatId),
        )

        assertEquals(session.messages.value, reopened.messages.value)
    }
}
