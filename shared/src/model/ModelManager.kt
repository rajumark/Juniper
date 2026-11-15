package raju.shingadiya.juniper.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ai.koog.prompt.executor.ollama.client.OllamaClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the model menu needs.
 *
 * - [ollamaReachable] is null until the first check, false when no Ollama server answers, and true otherwise.
 * - [installed] lists the model names Ollama reports.
 * - [error] is set when Ollama answered with an error.
 */
data class ModelSnapshot(
    val installed: List<String> = emptyList(),
    val ollamaReachable: Boolean? = null,
    val error: String? = null,
    val isRefreshing: Boolean = false,
) {
    fun isInstalled(name: String): Boolean = name.isNotBlank() && name in installed
}

/** Lists the models installed on the local Ollama server. */
fun interface ModelCatalog {
    suspend fun installedModels(): List<String>
}

/** [ModelCatalog] backed by Koog's Ollama client (`GET /api/tags` on the local server). */
class KoogModelCatalog(private val client: OllamaClient) : ModelCatalog {
    override suspend fun installedModels(): List<String> = client.getModels().map { it.name }
}

/** Tracks which local models Ollama has installed. */
class ModelManager(
    private val catalog: ModelCatalog,
    private val pollInterval: Duration = 5.seconds,
) {
    private val _state = MutableStateFlow(ModelSnapshot())
    val state: StateFlow<ModelSnapshot> = _state.asStateFlow()

    /** Checks the installed models every [pollInterval] until the returned job is cancelled. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (true) {
            refresh()
            delay(pollInterval)
        }
    }

    /** Runs one check now. Never throws; problems are reported in [state]. */
    suspend fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        try {
            val models = catalog.installedModels()
            _state.value = ModelSnapshot(installed = models, ollamaReachable = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Koog does not separate "server down" from other errors, so both show as a problem with Ollama.
            _state.value = ModelSnapshot(ollamaReachable = false, error = e.message ?: "Could not reach Ollama")
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    companion object {
        /** The model Juniper is built and tested with. Shown in the setup guide. */
        const val SUGGESTED_MODEL = "qwen2.5:7b"
    }
}
