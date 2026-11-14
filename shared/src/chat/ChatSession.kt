package raju.shingadiya.juniper.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * Holds one conversation with a local Ollama model: the visible messages, the streaming
 * state, and the history sent with each request. The UI observes [messages] and [isSending]
 * and calls [send], [clear] and, through its own coroutine job, cancels a reply in progress.
 */
class ChatSession(
    private val client: OllamaClient,
    val model: String,
    private val systemPrompt: String? = null,
) {
    private val _messages = MutableStateFlow<List<ChatBubble>>(emptyList())
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    /**
     * Sends [text] and streams the reply into the conversation. Returns without doing anything if
     * [text] is blank or a reply is already in progress. If the request fails, the placeholder
     * reply is replaced with an error bubble. Cancelling the calling coroutine keeps the partial reply.
     */
    suspend fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _isSending.value) return

        val request = ChatRequest(
            model = model,
            messages = buildRequestMessages() + ChatMessage(role = ChatBubble.ROLE_USER, content = prompt),
        )
        _messages.update { it + ChatBubble(ChatBubble.ROLE_USER, prompt) + ChatBubble(ChatBubble.ROLE_ASSISTANT, "") }
        _isSending.value = true
        try {
            client.streamChat(request).collect { chunk ->
                if (chunk.message.content.isNotEmpty()) {
                    appendToLastAssistant(chunk.message.content)
                }
            }
        } catch (e: OllamaException) {
            replaceLastAssistant(e.message ?: "Unknown error talking to Ollama")
        } finally {
            _isSending.value = false
        }
    }

    /** Removes all messages. Does not cancel a reply in progress; the caller should cancel its job first. */
    fun clear() {
        _messages.value = emptyList()
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
}
