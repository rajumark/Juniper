package raju.shingadiya.juniper.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Writes a debug trajectory for every chat: what the user sent, exactly what went to the model,
 * what came back, every tool call and approval, with timestamps.
 *
 * Each chat gets `<rootDir>/<chatId>/trajectory.jsonl`. The format is JSON Lines: every line is one
 * complete JSON object, so a crash mid-run still leaves a readable file, and each line can be
 * parsed on its own. Each line has `ts` (UTC time), `run` (one id per app launch), `chat_id`,
 * `event` and `details`.
 *
 * Writes happen on one background thread, in the order events were recorded, so logging never
 * slows the UI. Call [close] on shutdown to flush what is still queued.
 */
class TrajectoryRecorder(
    private val rootDir: File,
    private val clock: () -> Instant = Instant::now,
) : AutoCloseable {

    private val runId = UUID.randomUUID().toString()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "juniper-trajectory").apply { isDaemon = true }
    }

    /** Queues one event for [chatId]. The timestamp is taken now, not when the line is written. */
    fun record(chatId: String, event: String, details: JsonObject = JsonObject(emptyMap())) {
        val line = buildJsonObject {
            put("ts", clock().toString())
            put("run", runId)
            put("chat_id", chatId)
            put("event", event)
            put("details", details)
        }.toString()
        writer.execute { append(chatId, line) }
    }

    /** Waits up to two seconds for queued events to be written, then stops the writer. */
    override fun close() {
        writer.shutdown()
        writer.awaitTermination(2, TimeUnit.SECONDS)
    }

    /** The trajectory file for [chatId]. It may not exist yet until the chat's first event. */
    fun fileFor(chatId: String): File = File(File(rootDir, chatId), FILE_NAME)

    private fun append(chatId: String, line: String) {
        try {
            val file = fileFor(chatId)
            file.parentFile.mkdirs()
            file.appendText(line + "\n")
        } catch (e: Exception) {
            // A logging failure must never break a chat; report it and carry on.
            System.err.println("Could not write trajectory for chat $chatId: ${e.message}")
        }
    }

    companion object {
        const val FILE_NAME = "trajectory.jsonl"

        /** `~/.juniper/chats`, the folder that holds one subfolder per chat. */
        fun defaultDir(): File = File(File(System.getProperty("user.home"), ".juniper"), "chats")
    }
}
