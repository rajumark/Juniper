package raju.shingadiya.juniper.agent

import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.adb.AdbClient
import raju.shingadiya.juniper.chat.SqliteChatRepository
import raju.shingadiya.juniper.db.SqliteDatabase
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the real Koog agent against the local Ollama server. Opt-in: set `JUNIPER_LIVE=1` and make
 * sure Ollama is running with the model below. Skipped otherwise, so the normal test run stays offline.
 */
class LiveAgentTest {

    private val live = System.getenv("JUNIPER_LIVE") == "1"
    private val model = "qwen2.5:7b"

    private class CollectingObserver : AgentObserver {
        val tools = mutableListOf<String>()
        override fun onToolStarted(name: String, arguments: String) {
            tools += name
        }
        override fun onToolFinished(name: String, result: String, failed: Boolean) = Unit
    }

    @Test
    fun `agent answers a plain question through the local model`() = runTest {
        if (!live) return@runTest
        val connection = SqliteDatabase.open("jdbc:sqlite::memory:")
        val chat = SqliteChatRepository(connection).createChat(model)
        val runner = KoogChatAgentRunner(
            client = OllamaClient(),
            history = AgentHistoryStore(connection),
            adb = AdbClient(),
            selectedSerial = { null },
        )

        val reply = runner.run(chat.id, model, "Answer briefly.", "Reply with the single word: pong", CollectingObserver())

        println("LIVE reply: $reply")
        assertTrue(reply.isNotBlank(), "empty reply")
    }

    @Test
    fun `agent calls the Android version tool when asked about the device`() = runTest {
        if (!live) return@runTest
        val connection = SqliteDatabase.open("jdbc:sqlite::memory:")
        val chat = SqliteChatRepository(connection).createChat(model)
        val runner = KoogChatAgentRunner(
            client = OllamaClient(),
            history = AgentHistoryStore(connection),
            adb = AdbClient(),
            selectedSerial = { null },
        )
        val observer = CollectingObserver()

        runner.run(chat.id, model, "Use tools to answer.", "What is the Android version of the device?", observer)

        println("LIVE tools: ${observer.tools}")
        assertTrue("get_android_version" in observer.tools, "tools called: ${observer.tools}")
    }
}
