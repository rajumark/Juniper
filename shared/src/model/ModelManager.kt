package raju.shingadiya.juniper.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import raju.shingadiya.juniper.ollama.OllamaClient
import raju.shingadiya.juniper.ollama.OllamaException
import raju.shingadiya.juniper.ollama.OllamaUnreachableException
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

/** Tracks which local models Ollama has installed. */
class ModelManager(
    private val client: OllamaClient,
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
            val models = client.listModels()
            _state.value = ModelSnapshot(installed = models, ollamaReachable = true)
        } catch (e: OllamaUnreachableException) {
            _state.value = ModelSnapshot(ollamaReachable = false, error = e.message)
        } catch (e: OllamaException) {
            _state.update { it.copy(ollamaReachable = true, error = e.message, isRefreshing = false) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    companion object {
        /** The model Juniper is built and tested with. Shown in the setup guide. */
        const val SUGGESTED_MODEL = "qwen2.5:7b"
    }
}
