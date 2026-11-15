package raju.shingadiya.juniper.agent

import ai.koog.prompt.executor.ollama.client.OllamaClient
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

/** Juniper is for local models only: the Ollama client must point at this machine. */
class LocalOnlyTest {

    @Test
    fun `the Ollama client defaults to a loopback address`() {
        val host = URI(OllamaClient.DEFAULT_BASE_URL).host
        assertTrue(host in setOf("localhost", "127.0.0.1", "[::1]", "::1"), "not loopback: ${OllamaClient.DEFAULT_BASE_URL}")
    }
}
