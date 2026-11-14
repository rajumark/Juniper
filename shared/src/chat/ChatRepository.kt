package raju.shingadiya.juniper.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.UUID

/** Title given to a chat before its first message is sent. */
const val NEW_CHAT_TITLE = "New chat"

/** A chat as shown in the sidebar. [updatedAt] is epoch milliseconds. */
data class ChatSummary(
    val id: String,
    val title: String,
    val model: String,
    val updatedAt: Long,
)

/**
 * Storage for chats and their messages. Messages are addressed by [position], their index in
 * the conversation, so the UI's list and the stored rows always line up.
 */
interface ChatRepository {
    /** All chats, most recently updated first. Updated after every change. */
    val chats: StateFlow<List<ChatSummary>>

    suspend fun createChat(model: String): ChatSummary
    suspend fun renameChat(chatId: String, title: String)
    suspend fun deleteChat(chatId: String)
    suspend fun loadMessages(chatId: String): List<ChatBubble>
    suspend fun appendMessage(chatId: String, position: Int, bubble: ChatBubble)
    suspend fun updateMessage(chatId: String, position: Int, content: String, isError: Boolean)
    suspend fun clearMessages(chatId: String)
}

/**
 * [ChatRepository] backed by a SQLite [connection] (see [raju.shingadiya.juniper.db.SqliteDatabase]).
 * JDBC calls run on [Dispatchers.IO] and are serialized with a lock, since one connection is shared.
 */
class SqliteChatRepository(
    private val connection: Connection,
    private val clock: () -> Long = System::currentTimeMillis,
) : ChatRepository {

    private val lock = Any()
    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    override val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    init {
        _chats.value = queryChats()
    }

    override suspend fun createChat(model: String): ChatSummary = write {
        val now = clock()
        val chat = ChatSummary(id = UUID.randomUUID().toString(), title = NEW_CHAT_TITLE, model = model, updatedAt = now)
        connection.prepareStatement(
            "INSERT INTO chats (id, title, model, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        ).use { st ->
            st.setString(1, chat.id)
            st.setString(2, chat.title)
            st.setString(3, chat.model)
            st.setLong(4, now)
            st.setLong(5, now)
            st.executeUpdate()
        }
        chat
    }

    override suspend fun renameChat(chatId: String, title: String) = write {
        connection.prepareStatement("UPDATE chats SET title = ? WHERE id = ?").use { st ->
            st.setString(1, title)
            st.setString(2, chatId)
            st.executeUpdate()
        }
        Unit
    }

    override suspend fun deleteChat(chatId: String) = write {
        // Messages are removed by ON DELETE CASCADE (foreign keys are enabled when the database opens).
        connection.prepareStatement("DELETE FROM chats WHERE id = ?").use { st ->
            st.setString(1, chatId)
            st.executeUpdate()
        }
        Unit
    }

    override suspend fun loadMessages(chatId: String): List<ChatBubble> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            connection.prepareStatement(
                "SELECT role, content, is_error FROM messages WHERE chat_id = ? ORDER BY position",
            ).use { st ->
                st.setString(1, chatId)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(ChatBubble(role = rs.getString(1), content = rs.getString(2), isError = rs.getInt(3) != 0))
                        }
                    }
                }
            }
        }
    }

    override suspend fun appendMessage(chatId: String, position: Int, bubble: ChatBubble) = write {
        connection.prepareStatement(
            "INSERT INTO messages (chat_id, position, role, content, is_error) VALUES (?, ?, ?, ?, ?)",
        ).use { st ->
            st.setString(1, chatId)
            st.setInt(2, position)
            st.setString(3, bubble.role)
            st.setString(4, bubble.content)
            st.setInt(5, if (bubble.isError) 1 else 0)
            st.executeUpdate()
        }
        touch(chatId)
        Unit
    }

    override suspend fun updateMessage(chatId: String, position: Int, content: String, isError: Boolean) = write {
        connection.prepareStatement(
            "UPDATE messages SET content = ?, is_error = ? WHERE chat_id = ? AND position = ?",
        ).use { st ->
            st.setString(1, content)
            st.setInt(2, if (isError) 1 else 0)
            st.setString(3, chatId)
            st.setInt(4, position)
            st.executeUpdate()
        }
        touch(chatId)
        Unit
    }

    override suspend fun clearMessages(chatId: String) = write {
        connection.prepareStatement("DELETE FROM messages WHERE chat_id = ?").use { st ->
            st.setString(1, chatId)
            st.executeUpdate()
        }
        Unit
    }

    /** Runs [block] on the IO dispatcher under the lock, then republishes the chat list. */
    private suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val result = block()
            _chats.value = queryChats()
            result
        }
    }

    private fun touch(chatId: String) {
        connection.prepareStatement("UPDATE chats SET updated_at = ? WHERE id = ?").use { st ->
            st.setLong(1, clock())
            st.setString(2, chatId)
            st.executeUpdate()
        }
    }

    private fun queryChats(): List<ChatSummary> =
        connection.prepareStatement(
            // rowid breaks ties when two chats share a timestamp: the newer insert sorts first.
            "SELECT id, title, model, updated_at FROM chats ORDER BY updated_at DESC, rowid DESC",
        ).use { st ->
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(ChatSummary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)))
                    }
                }
            }
        }
}
