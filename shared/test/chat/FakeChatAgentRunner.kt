package raju.shingadiya.juniper.chat

import raju.shingadiya.juniper.agent.AgentObserver
import raju.shingadiya.juniper.agent.ChatAgentRunner

/**
 * Stands in for the Koog agent in tests. For each message it reports the given tool calls to the
 * observer, then returns [reply]. Set [failure] to make the next run throw.
 */
class FakeChatAgentRunner(
    private val toolCalls: List<Pair<String, String>> = emptyList(),
    private val reply: (String) -> String = { "answer to $it" },
) : ChatAgentRunner {
    val inputs = mutableListOf<String>()
    val models = mutableListOf<String>()
    val forgotten = mutableListOf<String>()
    var failure: Exception? = null

    override suspend fun run(
        chatId: String,
        model: String,
        systemPrompt: String?,
        input: String,
        observer: AgentObserver,
    ): String {
        inputs += input
        models += model
        failure?.let { throw it }
        for ((name, result) in toolCalls) {
            observer.onToolStarted(name, "{}")
            observer.onToolFinished(name, result, failed = false)
        }
        return reply(input)
    }

    override suspend fun forget(chatId: String) {
        forgotten += chatId
    }
}
