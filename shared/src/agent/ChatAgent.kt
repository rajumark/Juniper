package raju.shingadiya.juniper.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaLLMProvider
import raju.shingadiya.juniper.adb.AdbClient

/** What the agent reports while it works. The chat session turns these into chat bubbles and trajectory events. */
interface AgentObserver {
    fun onToolStarted(name: String, arguments: String)
    fun onToolFinished(name: String, result: String, failed: Boolean)
}

/** Runs one user message through the agent and returns the final reply text. */
interface ChatAgentRunner {
    suspend fun run(
        chatId: String,
        model: String,
        systemPrompt: String?,
        input: String,
        observer: AgentObserver,
    ): String

    /** Erases the agent's memory of [chatId], so the next message starts a fresh conversation. */
    suspend fun forget(chatId: String)
}

/**
 * Koog-backed [ChatAgentRunner] for local Ollama models only.
 *
 * Each message builds an [AIAgent] with Koog's chat strategy: Koog sends the conversation to the
 * model, runs the tools the model asks for, and repeats until it gives a final answer. Koog keeps
 * the conversation in [history] (the SQLite store), keyed by chat id. The agent stops after
 * [maxIterations] model calls.
 */
class KoogChatAgentRunner(
    private val client: OllamaClient,
    private val history: ChatHistoryProvider,
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
    private val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
) : ChatAgentRunner {

    override suspend fun run(
        chatId: String,
        model: String,
        systemPrompt: String?,
        input: String,
        observer: AgentObserver,
    ): String {
        val agent = AIAgent(
            promptExecutor = MultiLLMPromptExecutor(client),
            llmModel = LLModel(OllamaLLMProvider, model, listOf(LLMCapability.Tools, LLMCapability.Temperature)),
            strategy = singleRunStrategy(),
            toolRegistry = ToolRegistry { tools(readOnlyDeviceTools(adb, selectedSerial)) },
            systemPrompt = systemPrompt.orEmpty(),
            maxIterations = maxIterations,
        ) {
            install(ChatMemory.Feature) {
                chatHistoryProvider = history
                windowSize(HISTORY_WINDOW)
            }
            install(EventHandler.Feature) {
                onToolCallStarting { ctx -> observer.onToolStarted(ctx.toolName, ctx.toolArgs.toString()) }
                onToolCallCompleted { ctx -> observer.onToolFinished(ctx.toolName, ctx.toolResult.toString(), failed = false) }
                onToolCallFailed { ctx -> observer.onToolFinished(ctx.toolName, ctx.message, failed = true) }
            }
        }
        try {
            return agent.run(input, chatId)
        } finally {
            agent.close()
        }
    }

    override suspend fun forget(chatId: String) {
        history.store(chatId, emptyList())
    }

    companion object {
        const val DEFAULT_MAX_ITERATIONS = 8
        private const val HISTORY_WINDOW = 40
    }
}
