package raju.shingadiya.juniper.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One message in a chat conversation. [role] is "system", "user" or "assistant". */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

/** Sampling options passed to the model. Null fields are omitted from the request. */
@Serializable
data class ChatOptions(
    val temperature: Double? = null,
    @SerialName("num_ctx") val numCtx: Int? = null,
)

/**
 * Request body for `POST /api/chat`.
 *
 * [format] constrains the output. Pass a JSON schema object to force structured output,
 * or the string `"json"` (as a JSON primitive) for any valid JSON.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val format: JsonElement? = null,
    val options: ChatOptions? = null,
)

/**
 * One response object from `/api/chat`. When streaming, every line is a partial response
 * and the last one has [done] = true. [doneReason] is set only on that final line.
 */
@Serializable
data class ChatResponse(
    val model: String = "",
    val message: ChatMessage = ChatMessage(role = "assistant", content = ""),
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
)

@Serializable
internal data class TagsResponse(
    val models: List<ModelInfo> = emptyList(),
)

@Serializable
internal data class ModelInfo(
    val name: String,
)
