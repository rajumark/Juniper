package raju.shingadiya.juniper.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import raju.shingadiya.juniper.db.SqliteDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrajectoryTest {

    private val json = Json

    private fun tempDir(): File = Files.createTempDirectory("juniper-trajectory").toFile()

    /** Reads a chat's trajectory file; every line must be a complete JSON object. */
    private fun events(dir: File, chatId: String): List<JsonObject> =
        File(File(dir, chatId), TrajectoryRecorder.FILE_NAME).readLines()
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it).jsonObject }

    private fun names(lines: List<JsonObject>) = lines.map { it["event"]!!.jsonPrimitive.content }

    @Test
    fun `recorder writes one JSON line per event with timestamp, run and chat`() {
        val dir = tempDir()
        val recorder = TrajectoryRecorder(dir)

        recorder.record("chat-1", "first", JsonObject(emptyMap()))
        recorder.record("chat-1", "second")
        recorder.close()

        val lines = events(dir, "chat-1")
        assertEquals(listOf("first", "second"), names(lines))
        assertTrue(lines[0]["ts"]!!.jsonPrimitive.content.endsWith("Z"))
        assertEquals("chat-1", lines[0]["chat_id"]!!.jsonPrimitive.content)
        assertEquals(lines[0]["run"], lines[1]["run"])
    }

    @Test
    fun `a reply logs prompt, tool calls, tool results and how it ended`() = runTest {
        val dir = tempDir()
        val recorder = TrajectoryRecorder(dir)
        val repository = SqliteChatRepository(SqliteDatabase.open("jdbc:sqlite::memory:"))
        val chat = repository.createChat(model = "qwen2.5:7b")
        val session = ChatSession(
            runner = FakeChatAgentRunner(toolCalls = listOf("get_android_version" to "15")),
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            chatId = chat.id,
            model = "qwen2.5:7b",
            trajectory = recorder,
        )

        session.send("what version?")
        recorder.close()

        val lines = events(dir, chat.id)
        assertEquals(
            listOf("session_opened", "user_message", "tool_call_requested", "tool_result", "reply_end"),
            names(lines),
        )
        val call = lines.first { it["event"]!!.jsonPrimitive.content == "tool_call_requested" }["details"]!!.jsonObject
        assertEquals("get_android_version", call["tool"]!!.jsonPrimitive.content)
        val result = lines.first { it["event"]!!.jsonPrimitive.content == "tool_result" }["details"]!!.jsonObject
        assertEquals("15", result["result"]!!.jsonPrimitive.content)
        assertEquals("completed", lines.last()["details"]!!.jsonObject["outcome"]!!.jsonPrimitive.content)
    }
}
