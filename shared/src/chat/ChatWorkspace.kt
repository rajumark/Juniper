package raju.shingadiya.juniper.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import raju.shingadiya.juniper.agent.ChatAgentRunner

/**
 * Owns the chat list and which chat is open. Each chat gets one [ChatSession], kept in memory
 * while the app runs, so a reply that is still running continues when the user switches away.
 *
 * Call methods from the UI thread (for example inside `scope.launch`).
 */
class ChatWorkspace(
    private val runner: ChatAgentRunner,
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
    private val defaultModel: String,
    private val systemPrompt: String? = null,
    private val trajectory: TrajectoryRecorder? = null,
) {
    /** Sidebar list, most recently updated first. */
    val chats: StateFlow<List<ChatSummary>> = repository.chats

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    /** The chat shown on the right, or null while nothing is open. */
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    private val sessions = mutableMapOf<String, ChatSession>()

    /** Opens the most recent chat, or creates the first one. */
    suspend fun start() {
        val latest = chats.value.firstOrNull()
        if (latest == null) newChat() else select(latest.id)
    }

    suspend fun newChat() {
        val chat = repository.createChat(defaultModel)
        trajectory?.record(chat.id, "chat_created", buildJsonObject { put("model", defaultModel) })
        select(chat.id)
    }

    suspend fun select(chatId: String) {
        val session = sessions[chatId] ?: openSession(chatId) ?: return
        _activeSession.value = session
    }

    /** Gives the chat a new title. Blank titles are ignored. */
    suspend fun rename(chatId: String, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) {
            repository.renameChat(chatId, clean)
            trajectory?.record(chatId, "chat_renamed", buildJsonObject { put("title", clean) })
        }
    }

    /** Deletes the chat and its messages. If it was open, the most recent remaining chat opens instead. */
    suspend fun delete(chatId: String) {
        sessions.remove(chatId)?.stop()
        repository.deleteChat(chatId)
        trajectory?.record(chatId, "chat_deleted")
        if (_activeSession.value?.chatId == chatId) {
            _activeSession.value = null
            val next = chats.value.firstOrNull()
            if (next == null) newChat() else select(next.id)
        }
    }

    private suspend fun openSession(chatId: String): ChatSession? {
        val summary = chats.value.find { it.id == chatId } ?: return null
        val session = ChatSession(
            runner = runner,
            repository = repository,
            scope = scope,
            chatId = summary.id,
            model = summary.model,
            systemPrompt = systemPrompt,
            initialMessages = repository.loadMessages(chatId),
            trajectory = trajectory,
        )
        sessions[chatId] = session
        return session
    }
}
