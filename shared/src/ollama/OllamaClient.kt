package raju.shingadiya.juniper.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.io.IOException

/** Raised when Ollama is unreachable, returns an HTTP error, or sends a malformed response. */
open class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Raised when no Ollama server answers at the configured address (usually: Ollama is not running). */
class OllamaUnreachableException(message: String, cause: Throwable? = null) : OllamaException(message, cause)

/**
 * Client for the Ollama HTTP API running on this machine.
 *
 * Use [chat] for a single complete reply, or [streamChat] to receive tokens as they are generated.
 * Create one instance and reuse it; call [close] when the app shuts down.
 */
class OllamaClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = createDefaultHttpClient(),
) : AutoCloseable {

    /** Sends [request] and returns the complete reply. Any `stream` value in [request] is ignored. */
    suspend fun chat(request: ChatRequest): ChatResponse {
        val response = wrapIo {
            httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(stream = false))
            }
        }
        ensureSuccess(response)
        return response.body()
    }

    /**
     * Sends [request] with streaming enabled and emits each partial [ChatResponse] as it arrives.
     * The flow completes after the chunk with `done = true`.
     */
    fun streamChat(request: ChatRequest): Flow<ChatResponse> = flow {
        wrapIo {
            httpClient.preparePost("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(stream = true))
            }.execute { response ->
                ensureSuccess(response)
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    val chunk = decodeLine(line)
                    emit(chunk)
                    if (chunk.done) break
                }
            }
        }
    }

    /** Returns the names of the models installed in Ollama, for example `qwen2.5:7b`. */
    suspend fun listModels(): List<String> {
        val response = wrapIo { httpClient.get("$baseUrl/api/tags") }
        ensureSuccess(response)
        return response.body<TagsResponse>().models.map { it.name }
    }

    override fun close() {
        httpClient.close()
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw OllamaException("Ollama returned HTTP ${response.status.value}: $body")
        }
    }

    private fun decodeLine(line: String): ChatResponse = try {
        json.decodeFromString(ChatResponse.serializer(), line)
    } catch (e: Exception) {
        throw OllamaException("Could not parse Ollama response line: $line", e)
    }

    private suspend fun <T> wrapIo(block: suspend () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw OllamaUnreachableException("Cannot reach Ollama at $baseUrl. Is it running?", e)
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"

        internal val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun createDefaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                // Local models can take a long time before the first token; do not cut them off.
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 5_000
            }
        }
    }
}
