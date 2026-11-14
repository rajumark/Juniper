package raju.shingadiya.juniper.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import raju.shingadiya.juniper.ollama.ChatMessage
import raju.shingadiya.juniper.ollama.ChatRequest
import raju.shingadiya.juniper.ollama.OllamaClient
import raju.shingadiya.juniper.ollama.OllamaException

/** One message shown in the chat window. [role] is "user" or "assistant". */
data class ChatBubble(
    val role: String,
    val content: String,
    val isError: Boolean = false,
) {
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/**
 * One conversation with a local Ollama model: the visible messages, the streaming state, and the
 * history sent with each request. Every message is written to [repository] as it is created, and
 * the final text of each reply is saved when the reply ends, including when it is stopped.
 *
 * [submit] runs the reply in [scope], so it keeps streaming if the user switches to another chat.
 */
class ChatSession(
    private val client: OllamaClient,
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
    val chatId: String,
    model: String,
    private val systemPrompt: String? = null,
    initialMessages: List<ChatBubble> = emptyList(),
) {
    private val _messages = MutableStateFlow(initialMessages)
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _model = MutableStateFlow(model)
    /** The model this chat sends to. Blank means no model is selected yet. */
    val currentModel: StateFlow<String> = _model.asStateFlow()
    val model: String get() = _model.value

    /** Switches the model used for the next reply. Replies already in progress keep their model. */
    fun selectModel(name: String) {
        _model.value = name.trim()
    }

    private var replyJob: Job? = null

    /** Starts [send] in the background. Ignored while a reply is already in progress. */
    fun submit(text: String) {
        if (_isSending.value || text.isBlank()) return
        // UNDISPATCHED sets isSending before this returns, so a second click cannot start another reply.
        replyJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { send(text) }
    }

    /** Stops the reply in progress. The text streamed so far is kept. */
    fun stop() {
        replyJob?.cancel()
    }

    /** Stops any reply in progress, then removes all messages from this chat. */
    suspend fun clear() {
        replyJob?.cancelAndJoin()
        _messages.value = emptyList()
        repository.clearMessages(chatId)
    }

    /**
     * Sends [text] and streams the reply into the conversation. Returns without doing anything if
     * [text] is blank or a reply is already in progress. If the request fails, the placeholder
     * reply is replaced with an error bubble. Cancelling the calling coroutine keeps the partial reply.
     */
    suspend fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _isSending.value || model.isBlank()) return
        _isSending.value = true

        val isFirstMessage = _messages.value.isEmpty()
        val request = ChatRequest(
            model = model,
            messages = buildRequestMessages() + ChatMessage(role = ChatBubble.ROLE_USER, content = prompt),
        )
        var replyPosition: Int? = null
        try {
            val position = _messages.value.size
            repository.appendMessage(chatId, position, ChatBubble(ChatBubble.ROLE_USER, prompt))
            repository.appendMessage(chatId, position + 1, ChatBubble(ChatBubble.ROLE_ASSISTANT, ""))
            // Only the default title is replaced; a title the user chose by hand is kept.
            val hasDefaultTitle = repository.chats.value.find { it.id == chatId }?.title == NEW_CHAT_TITLE
            if (isFirstMessage && hasDefaultTitle) repository.renameChat(chatId, titleFrom(prompt))
            _messages.update { it + ChatBubble(ChatBubble.ROLE_USER, prompt) + ChatBubble(ChatBubble.ROLE_ASSISTANT, "") }
            replyPosition = position + 1

            client.streamChat(request).collect { chunk ->
                if (chunk.message.content.isNotEmpty()) {
                    appendToLastAssistant(chunk.message.content)
                }
            }
        } catch (e: OllamaException) {
            replaceLastAssistant(e.message ?: "Unknown error talking to Ollama")
        } finally {
            // Save the reply even when it was stopped; NonCancellable lets the write finish.
            withContext(NonCancellable) {
                val position = replyPosition
                val reply = position?.let { _messages.value.getOrNull(it) }
                if (position != null && reply != null) {
                    repository.updateMessage(chatId, position, reply.content, reply.isError)
                }
            }
            _isSending.value = false
        }
    }

    /** Builds the history sent to the model: the optional system prompt plus completed exchanges. */
    private fun buildRequestMessages(): List<ChatMessage> {
        val history = _messages.value
            .filter { !it.isError && it.content.isNotEmpty() }
            .map { ChatMessage(role = it.role, content = it.content) }
        return listOfNotNull(systemPrompt?.let { ChatMessage(role = "system", content = it) }) + history
    }

    private fun appendToLastAssistant(fragment: String) {
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            list.dropLast(1) + list.last().copy(content = list.last().content + fragment)
        }
    }

    private fun replaceLastAssistant(errorText: String) {
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            list.dropLast(1) + list.last().copy(content = errorText, isError = true)
        }
    }

    /** Sidebar title for a new chat: the first line of the first message, shortened. */
    private fun titleFrom(prompt: String): String {
        val firstLine = prompt.lineSequence().first().trim()
        return if (firstLine.length <= TITLE_MAX_LENGTH) firstLine else firstLine.take(TITLE_MAX_LENGTH).trimEnd() + "…"
    }

    private companion object {
        const val TITLE_MAX_LENGTH = 40
    }
}
