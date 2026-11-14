package raju.shingadiya.juniper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import raju.shingadiya.juniper.chat.ChatScreen
import raju.shingadiya.juniper.chat.ChatSession
import raju.shingadiya.juniper.ollama.OllamaClient
import raju.shingadiya.juniper.ui.JuniperTheme

private const val DEFAULT_MODEL = "qwen2.5:7b"
private const val SYSTEM_PROMPT = "You are Juniper, a helpful assistant for Android developers."

@Composable
@DevelopmentEntryPoint
fun App() {
    JuniperTheme {
        val client = remember { OllamaClient() }
        DisposableEffect(client) {
            onDispose { client.close() }
        }
        val session = remember { ChatSession(client, model = DEFAULT_MODEL, systemPrompt = SYSTEM_PROMPT) }
        ChatScreen(session)
    }
}
