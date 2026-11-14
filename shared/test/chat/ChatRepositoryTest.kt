package raju.shingadiya.juniper.chat

import raju.shingadiya.juniper.db.SqliteDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRepositoryTest {

    private var now = 1_000L
    private val repository = SqliteChatRepository(
        connection = SqliteDatabase.open("jdbc:sqlite::memory:"),
        clock = { now },
    )

    @Test
    fun `created chat is listed with the default title`() = runTest {
        val chat = repository.createChat("qwen2.5:7b")

        assertEquals(listOf(chat), repository.chats.value)
        assertEquals(NEW_CHAT_TITLE, chat.title)
        assertEquals("qwen2.5:7b", chat.model)
    }

    @Test
    fun `chats are listed most recently updated first`() = runTest {
        now = 1_000
        val older = repository.createChat("m")
        now = 2_000
        val newer = repository.createChat("m")
        assertEquals(listOf(newer.id, older.id), repository.chats.value.map { it.id })

        now = 3_000
        repository.appendMessage(older.id, 0, ChatBubble("user", "hi"))

        assertEquals(listOf(older.id, newer.id), repository.chats.value.map { it.id })
    }

    @Test
    fun `rename changes the listed title`() = runTest {
        val chat = repository.createChat("m")

        repository.renameChat(chat.id, "Kotlin questions")

        assertEquals("Kotlin questions", repository.chats.value.single().title)
    }

    @Test
    fun `messages are loaded in position order with their flags`() = runTest {
        val chat = repository.createChat("m")
        repository.appendMessage(chat.id, 0, ChatBubble("user", "question"))
        repository.appendMessage(chat.id, 1, ChatBubble("assistant", ""))
        repository.updateMessage(chat.id, 1, "answer", isError = false)
        repository.appendMessage(chat.id, 2, ChatBubble("user", "again"))

        assertEquals(
            listOf(
                ChatBubble("user", "question"),
                ChatBubble("assistant", "answer"),
                ChatBubble("user", "again"),
            ),
            repository.loadMessages(chat.id),
        )
    }

    @Test
    fun `updating a message to an error sets the flag`() = runTest {
        val chat = repository.createChat("m")
        repository.appendMessage(chat.id, 0, ChatBubble("assistant", ""))

        repository.updateMessage(chat.id, 0, "boom", isError = true)

        assertEquals(ChatBubble("assistant", "boom", isError = true), repository.loadMessages(chat.id).single())
    }

    @Test
    fun `clear removes only the messages of that chat`() = runTest {
        val first = repository.createChat("m")
        val second = repository.createChat("m")
        repository.appendMessage(first.id, 0, ChatBubble("user", "a"))
        repository.appendMessage(second.id, 0, ChatBubble("user", "b"))

        repository.clearMessages(first.id)

        assertTrue(repository.loadMessages(first.id).isEmpty())
        assertEquals(1, repository.loadMessages(second.id).size)
    }

    @Test
    fun `deleting a chat removes its messages`() = runTest {
        val chat = repository.createChat("m")
        repository.appendMessage(chat.id, 0, ChatBubble("user", "gone soon"))

        repository.deleteChat(chat.id)

        assertTrue(repository.chats.value.isEmpty())
        assertTrue(repository.loadMessages(chat.id).isEmpty())
    }
}
