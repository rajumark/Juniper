package raju.shingadiya.juniper.agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.sql.Connection

/**
 * Koog's chat memory, stored in the app's SQLite database. Koog calls [store] after each run and
 * [load] before the next one, so the model sees the whole conversation when a chat is reopened.
 *
 * Each conversation id is a chat id. Messages are kept in order, one JSON document per row.
 * [connection] is shared with the chat repository, so writes go through the same lock.
 */
class AgentHistoryStore(
    private val connection: Connection,
) : ChatHistoryProvider {

    override suspend fun store(conversationId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.autoCommit = false
            try {
                connection.prepareStatement("DELETE FROM agent_messages WHERE chat_id = ?").use { st ->
                    st.setString(1, conversationId)
                    st.executeUpdate()
                }
                connection.prepareStatement("INSERT INTO agent_messages (chat_id, position, message) VALUES (?, ?, ?)").use { st ->
                    messages.forEachIndexed { index, message ->
                        st.setString(1, conversationId)
                        st.setInt(2, index)
                        st.setString(3, JSON.encodeToString(Message.serializer(), message))
                        st.addBatch()
                    }
                    st.executeBatch()
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
        Unit
    }

    override suspend fun load(conversationId: String): List<Message> = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement("SELECT message FROM agent_messages WHERE chat_id = ? ORDER BY position").use { st ->
                st.setString(1, conversationId)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(JSON.decodeFromString(Message.serializer(), rs.getString(1)))
                    }
                }
            }
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
