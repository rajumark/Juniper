package raju.shingadiya.juniper.model

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.ollama.OllamaClient
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelManagerTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun managerFor(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): ModelManager {
        val engine = MockEngine { _ -> handler() }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(OllamaClient.json) } }
        return ModelManager(OllamaClient(baseUrl = "http://test", httpClient = http))
    }

    @Test
    fun `lists installed models when Ollama is running`() = runTest {
        val manager = managerFor {
            respond(
                content = """{"models":[{"name":"qwen2.5:7b"},{"name":"llama3:8b"}]}""",
                headers = jsonHeaders,
            )
        }

        manager.refresh()

        val state = manager.state.value
        assertEquals(listOf("qwen2.5:7b", "llama3:8b"), state.installed)
        assertEquals(true, state.ollamaReachable)
        assertNull(state.error)
        assertTrue(state.isInstalled(ModelManager.SUGGESTED_MODEL))
        assertFalse(state.isInstalled(""))
        assertFalse(state.isInstalled("mistral:7b"))
    }

    @Test
    fun `Ollama not running is reported as unreachable with no models`() = runTest {
        val manager = managerFor { throw ConnectException("Connection refused") }

        manager.refresh()

        val state = manager.state.value
        assertEquals(false, state.ollamaReachable)
        assertTrue(state.installed.isEmpty())
        assertTrue(state.error!!.contains("Is it running?"))
    }

    @Test
    fun `server error keeps Ollama reachable and shows the message`() = runTest {
        val manager = managerFor { respondError(HttpStatusCode.InternalServerError, "registry down") }

        manager.refresh()

        val state = manager.state.value
        assertEquals(true, state.ollamaReachable)
        assertTrue(state.error!!.contains("registry down"))
    }

    @Test
    fun `refresh finishes with isRefreshing cleared`() = runTest {
        val manager = managerFor { throw ConnectException("refused") }

        manager.refresh()

        assertFalse(manager.state.value.isRefreshing)
    }
}
