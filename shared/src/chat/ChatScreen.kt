package raju.shingadiya.juniper.chat

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.compose.resources.painterResource
import raju.shingadiya.juniper.resources.Res
import raju.shingadiya.juniper.resources.ic_copy
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import raju.shingadiya.juniper.ui.JuniperSpacing

/** Right pane: the open chat's messages and input box. Talks to the model through [session]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    session: ChatSession,
    title: String,
    modifier: Modifier = Modifier,
    chipBar: @Composable () -> Unit = {},
) {
    val messages by session.messages.collectAsState()
    val isSending by session.isSending.collectAsState()
    val model by session.currentModel.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    // Keep the newest message in view while a reply streams in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty() || isSending) return
        input = ""
        session.submit(text)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    TextButton(
                        onClick = { scope.launch { session.clear() } },
                        enabled = !isSending,
                    ) {
                        Text("Clear")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(JuniperSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(JuniperSpacing.sm),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Button(onClick = session::stop) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = ::submit, enabled = input.isNotBlank() && model.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
            chipBar()
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
fun EmptyState(text: String = "Ask the local model anything.", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(JuniperSpacing.lg), contentAlignment = Alignment.Center) {
        Text(
            text = text,
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource),
        horizontalArrangement = if (bubble.isUser) Arrangement.End else Arrangement.Start,
    ) {
        // The copy button is drawn over the corner of the bubble, so showing or hiding it never resizes anything.
        Box(modifier = Modifier.widthIn(max = JuniperSpacing.bubbleMaxWidth)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor),
            ) {
                SelectionContainer {
                    if (bubble.isUser || bubble.isError) {
                        Text(
                            text = bubble.content.ifEmpty { "…" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            modifier = Modifier.padding(JuniperSpacing.md),
                        )
                    } else {
                        MarkdownContent(
                            source = bubble.content.ifEmpty { "…" },
                            color = contentColor,
                            modifier = Modifier.padding(JuniperSpacing.md),
                        )
                    }
                }
            }
            if (hovered && bubble.content.isNotEmpty()) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(bubble.content)) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_copy),
                        contentDescription = "Copy message",
                        tint = contentColor,
                    )
                }
            }
        }
    }
}
