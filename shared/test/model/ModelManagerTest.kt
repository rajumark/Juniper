package raju.shingadiya.juniper.model

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelManagerTest {

    @Test
    fun `lists installed models when Ollama is running`() = runTest {
        val manager = ModelManager(ModelCatalog { listOf("qwen2.5:7b", "llama3:8b") })

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
    fun `a failing catalog is reported as unreachable with no models`() = runTest {
        val manager = ModelManager(ModelCatalog { throw IllegalStateException("Connection refused") })

        manager.refresh()

        val state = manager.state.value
        assertEquals(false, state.ollamaReachable)
        assertTrue(state.installed.isEmpty())
        assertEquals("Connection refused", state.error)
    }

    @Test
    fun `refresh finishes with isRefreshing cleared`() = runTest {
        val manager = ModelManager(ModelCatalog { throw IllegalStateException("refused") })

        manager.refresh()

        assertFalse(manager.state.value.isRefreshing)
    }
}
