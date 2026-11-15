package raju.shingadiya.juniper.agent

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.chat.SqliteChatRepository
import raju.shingadiya.juniper.db.SqliteDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentHistoryStoreTest {

    private val connection = SqliteDatabase.open("jdbc:sqlite::memory:")
    private val repository = SqliteChatRepository(connection)
    private val store = AgentHistoryStore(connection)

    @Test
    fun `stored messages load back in the same order`() = runTest {
        val chat = repository.createChat("qwen2.5:7b")
        val messages = listOf(
            Message.User("what version?", RequestMetaInfo.Empty),
            Message.Assistant("Android 15.", ResponseMetaInfo.Empty),
        )

        store.store(chat.id, messages)

        assertEquals(messages, store.load(chat.id))
    }

    @Test
    fun `storing again replaces the earlier history`() = runTest {
        val chat = repository.createChat("qwen2.5:7b")
        store.store(chat.id, listOf(Message.User("first", RequestMetaInfo.Empty)))

        store.store(chat.id, listOf(Message.User("second", RequestMetaInfo.Empty)))

        assertEquals(listOf<Message>(Message.User("second", RequestMetaInfo.Empty)), store.load(chat.id))
    }

    @Test
    fun `an empty store clears the history`() = runTest {
        val chat = repository.createChat("qwen2.5:7b")
        store.store(chat.id, listOf(Message.User("hi", RequestMetaInfo.Empty)))

        store.store(chat.id, emptyList())

        assertEquals(emptyList(), store.load(chat.id))
    }

    @Test
    fun `deleting a chat removes its agent history`() = runTest {
        val chat = repository.createChat("qwen2.5:7b")
        store.store(chat.id, listOf(Message.User("hi", RequestMetaInfo.Empty)))

        repository.deleteChat(chat.id)

        assertEquals(emptyList(), store.load(chat.id))
    }
}
