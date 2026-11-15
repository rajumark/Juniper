package raju.shingadiya.juniper.chat

import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import raju.shingadiya.juniper.agent.AgentObserver
import raju.shingadiya.juniper.agent.ChatAgentRunner

/**
 * One message shown in the chat window. [role] is "user", "assistant" or "tool".
 * Tool bubbles also carry the tool's [toolName] and the JSON [toolInput] it was called with;
 * their [content] is the result.
 */
data class ChatBubble(
    val role: String,
    val content: String,
    val isError: Boolean = false,
    val toolName: String? = null,
    val toolInput: String? = null,
) {
    val isUser: Boolean get() = role == ROLE_USER
    val isTool: Boolean get() = role == ROLE_TOOL

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"
    }
}

/**
 * One conversation: the visible messages, the sending state, and the model it uses.
 *
 * Each message is handed to [runner], which runs the Koog agent. The agent remembers the
 * conversation itself; this class only keeps what the window shows and saves it to [repository].
 * Tool calls show up as tool bubbles while the agent works. The reply and the tool bubbles are
 * saved when the reply ends, including when it is stopped.
 *
 * When [trajectory] is set, every step is logged to the chat's trajectory file.
 *
 * [submit] runs the reply in [scope], so it keeps going if the user switches to another chat.
 */
class ChatSession(
    private val runner: ChatAgentRunner,
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
    val chatId: String,
    model: String,
    private val systemPrompt: String? = null,
    initialMessages: List<ChatBubble> = emptyList(),
    private val trajectory: TrajectoryRecorder? = null,
) {
    private val _messages = MutableStateFlow(initialMessages)
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _model = MutableStateFlow(model)
    /** The model this chat sends to. Blank means no model is selected yet. */
    val currentModel: StateFlow<String> = _model.asStateFlow()
    val model: String get() = _model.value

    init {
        log("session_opened", buildJsonObject {
            put("model", model)
            put("system_prompt", systemPrompt)
            put("history_messages", initialMessages.size)
        })
    }

    /** Full path of this chat's trajectory file, or null when trajectories are off. */
    val trajectoryPath: String? get() = trajectory?.fileFor(chatId)?.absolutePath

    /** Switches the model used for the next reply. Replies already in progress keep their model. */
    fun selectModel(name: String) {
        val clean = name.trim()
        _model.value = clean
        log("model_selected", buildJsonObject { put("model", clean) })
    }

    private var replyJob: Job? = null

    /** Starts [send] in the background. Ignored while a reply is already in progress. */
    fun submit(text: String) {
        if (_isSending.value || text.isBlank()) return
        // UNDISPATCHED sets isSending before this returns, so a second click cannot start another reply.
        replyJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { send(text) }
    }

    /** Stops the reply in progress. What was produced so far is kept. */
    fun stop() {
        log("stop_requested")
        replyJob?.cancel()
    }

    /** Stops any reply in progress, then removes all messages and the agent's memory of this chat. */
    suspend fun clear() {
        replyJob?.cancelAndJoin()
        _messages.value = emptyList()
        repository.clearMessages(chatId)
        runner.forget(chatId)
        log("chat_cleared")
    }

    /**
     * Sends [text] and waits for the reply. Returns without doing anything if [text] is blank, the
     * model is not set, or a reply is already in progress. If the run fails, the failure is shown as
     * an error bubble. Cancelling the calling coroutine keeps everything produced so far.
     */
    suspend fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _isSending.value || model.isBlank()) return
        _isSending.value = true

        val startedAt = System.nanoTime()
        val isFirstMessage = _messages.value.isEmpty()
        // Bubbles produced by this reply that are shown but not yet saved. They are always the tail of the list.
        val unsaved = mutableListOf<ChatBubble>()
        var outcome = "interrupted"
        try {
            log("user_message", buildJsonObject { put("text", prompt) })
            appendBubble(ChatBubble(ChatBubble.ROLE_USER, prompt))
            // Only the default title is replaced; a title the user chose by hand is kept.
            val hasDefaultTitle = repository.chats.value.find { it.id == chatId }?.title == NEW_CHAT_TITLE
            if (isFirstMessage && hasDefaultTitle) repository.renameChat(chatId, titleFrom(prompt))

            val reply = runner.run(chatId, model, systemPrompt, prompt, ToolObserver(unsaved))
            show(unsaved, ChatBubble(ChatBubble.ROLE_ASSISTANT, reply))
            outcome = "completed"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("agent_error", buildJsonObject {
                put("exception", e::class.simpleName)
                put("message", e.message)
            })
            show(unsaved, ChatBubble(ChatBubble.ROLE_ASSISTANT, e.message ?: "Unknown error while running the model", isError = true))
            outcome = "error"
        } finally {
            withContext(NonCancellable) {
                saveTail(unsaved)
                log("reply_end", buildJsonObject {
                    put("outcome", outcome)
                    put("duration_ms", (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
                })
                _isSending.value = false
            }
        }
    }

    /** Receives tool activity from the agent and shows it as tool bubbles. */
    private inner class ToolObserver(private val unsaved: MutableList<ChatBubble>) : AgentObserver {
        private val pendingArguments = mutableMapOf<String, String>()

        override fun onToolStarted(name: String, arguments: String) {
            pendingArguments[name] = arguments
            log("tool_call_requested", buildJsonObject {
                put("tool", name)
                put("arguments", arguments)
            })
        }

        override fun onToolFinished(name: String, result: String, failed: Boolean) {
            val arguments = pendingArguments.remove(name)
            show(unsaved, ChatBubble(
                role = ChatBubble.ROLE_TOOL,
                content = result,
                isError = failed,
                toolName = name,
                toolInput = arguments,
            ))
            log("tool_result", buildJsonObject {
                put("tool", name)
                put("is_error", failed)
                put("result", result)
            })
        }
    }

    /** Adds a bubble that is saved later, when the reply ends. */
    private fun show(unsaved: MutableList<ChatBubble>, bubble: ChatBubble) {
        unsaved += bubble
        _messages.update { it + bubble }
    }

    /** Adds a message to the end of the chat and saves it right away. */
    private suspend fun appendBubble(bubble: ChatBubble) {
        val position = _messages.value.size
        repository.appendMessage(chatId, position, bubble)
        _messages.update { it + bubble }
    }

    /** Saves the bubbles from [unsaved], which are the last entries of the message list. */
    private suspend fun saveTail(unsaved: MutableList<ChatBubble>) {
        if (unsaved.isEmpty()) return
        val first = _messages.value.size - unsaved.size
        unsaved.forEachIndexed { i, bubble -> repository.appendMessage(chatId, first + i, bubble) }
        unsaved.clear()
    }

    private fun log(event: String, details: JsonObject = JsonObject(emptyMap())) {
        trajectory?.record(chatId, event, details)
    }

    /** Sidebar title for a new chat: the first line of the first message, shortened. */
    private fun titleFrom(prompt: String): String {
        val firstLine = prompt.lineSequence().first().trim()
        return if (firstLine.length <= TITLE_MAX_LENGTH) firstLine else firstLine.take(TITLE_MAX_LENGTH).trimEnd() + "…"
    }

    private companion object {
        const val TITLE_MAX_LENGTH = 40
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
