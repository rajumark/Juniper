package raju.shingadiya.juniper.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.db.SqliteDatabase
import raju.shingadiya.juniper.ollama.OllamaClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ChatWorkspaceTest {

    private fun workspace(repository: ChatRepository): ChatWorkspace {
        val http = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) {
            install(ContentNegotiation) { json(OllamaClient.json) }
        }
        return ChatWorkspace(
            client = OllamaClient(baseUrl = "http://test", httpClient = http),
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            defaultModel = "qwen2.5:7b",
        )
    }

    private fun repository() = SqliteChatRepository(SqliteDatabase.open("jdbc:sqlite::memory:"))

    @Test
    fun `start creates a first chat when there are none`() = runTest {
        val repository = repository()
        val workspace = workspace(repository)

        workspace.start()

        assertEquals(1, repository.chats.value.size)
        assertEquals(repository.chats.value.single().id, workspace.activeSession.value?.chatId)
    }

    @Test
    fun `start opens the most recent existing chat`() = runTest {
        val repository = repository()
        repository.createChat("m")
        val latest = repository.createChat("m")

        val workspace = workspace(repository)
        workspace.start()

        assertEquals(latest.id, workspace.activeSession.value?.chatId)
    }

    @Test
    fun `new chat becomes the active chat`() = runTest {
        val repository = repository()
        val workspace = workspace(repository)
        workspace.start()
        val previous = workspace.activeSession.value?.chatId

        workspace.newChat()

        assertNotEquals(previous, workspace.activeSession.value?.chatId)
        assertEquals(2, repository.chats.value.size)
    }

    @Test
    fun `deleting the open chat opens the next remaining chat`() = runTest {
        val repository = repository()
        val workspace = workspace(repository)
        workspace.start()
        workspace.newChat()
        val open = workspace.activeSession.value!!.chatId
        val other = repository.chats.value.first { it.id != open }.id

        workspace.delete(open)

        assertEquals(listOf(other), repository.chats.value.map { it.id })
        assertEquals(other, workspace.activeSession.value?.chatId)
    }

    @Test
    fun `deleting the last chat creates a new one`() = runTest {
        val repository = repository()
        val workspace = workspace(repository)
        workspace.start()
        val only = workspace.activeSession.value!!.chatId

        workspace.delete(only)

        assertEquals(1, repository.chats.value.size)
        assertNotEquals(only, workspace.activeSession.value?.chatId)
    }

    @Test
    fun `rename trims the title and ignores blank input`() = runTest {
        val repository = repository()
        val workspace = workspace(repository)
        workspace.start()
        val id = workspace.activeSession.value!!.chatId

        workspace.rename(id, "  Kotlin notes  ")
        assertEquals("Kotlin notes", repository.chats.value.single().title)

        workspace.rename(id, "   ")
        assertEquals("Kotlin notes", repository.chats.value.single().title)
    }

    @Test
    fun `active session is null before start`() = runTest {
        assertNull(workspace(repository()).activeSession.value)
    }
}
