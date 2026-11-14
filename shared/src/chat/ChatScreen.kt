package raju.shingadiya.juniper.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import raju.shingadiya.juniper.ui.JuniperSpacing

/** Chat window with a message list and an input box. Talks to the model through [session]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(session: ChatSession, modifier: Modifier = Modifier) {
    val messages by session.messages.collectAsState()
    val isSending by session.isSending.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var replyJob by remember { mutableStateOf<Job?>(null) }

    // Keep the newest message in view while a reply streams in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty() || isSending) return
        input = ""
        replyJob = scope.launch { session.send(text) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Juniper · ${session.model}") },
                actions = {
                    TextButton(
                        onClick = { replyJob?.cancel(); session.clear() },
                        enabled = !isSending,
                    ) {
                        Text("Clear")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(JuniperSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            // Enter sends; Shift+Enter inserts a new line.
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                submit()
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Message the model") },
                    maxLines = 6,
                )
                Spacer(Modifier.width(JuniperSpacing.sm))
                if (isSending) {
                    Button(onClick = { replyJob?.cancel() }) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = ::submit, enabled = input.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isSending) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (messages.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(JuniperSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(JuniperSpacing.sm),
                ) {
                    items(messages) { bubble -> MessageBubble(bubble) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(JuniperSpacing.lg), contentAlignment = Alignment.Center) {
        Text(
            text = "Ask the local model anything.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val colors = MaterialTheme.colorScheme
    val containerColor = when {
        bubble.isError -> colors.errorContainer
        bubble.isUser -> colors.primaryContainer
        else -> colors.surfaceVariant
    }
    val contentColor = when {
        bubble.isError -> colors.onErrorContainer
        bubble.isUser -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (bubble.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.widthIn(max = JuniperSpacing.bubbleMaxWidth),
        ) {
            SelectionContainer {
                Text(
                    text = bubble.content.ifEmpty { "…" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    modifier = Modifier.padding(JuniperSpacing.md),
                )
            }
        }
    }
}
