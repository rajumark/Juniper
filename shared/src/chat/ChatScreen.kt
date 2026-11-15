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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.compose.resources.painterResource
import raju.shingadiya.juniper.resources.Res
import raju.shingadiya.juniper.resources.ic_copy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import raju.shingadiya.juniper.resources.ic_send
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
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }

    // Tool calls stay in the saved history but are not shown: the chat shows only the final answer.
    val visibleMessages = remember(messages) { messages.filterNot { it.isTool } }

    // Keep the newest message in view while a reply streams in.
    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.content) {
        if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.lastIndex)
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
                    IconButton(onClick = { session.trajectoryPath?.let { clipboard.setText(AnnotatedString(it)) } }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_copy),
                            contentDescription = "Copy trajectory file path",
                        )
                    }
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
            // The composer is one pill: a borderless text field with the send button inside it.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = JuniperSpacing.lg, end = JuniperSpacing.sm, top = JuniperSpacing.xs, bottom = JuniperSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    colors = composerFieldColors(),
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
                    FilledIconButton(
                        onClick = ::submit,
                        enabled = input.isNotBlank() && model.isNotBlank(),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_send),
                            contentDescription = "Send message",
                        )
                    }
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
            if (visibleMessages.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = JuniperSpacing.xl, vertical = JuniperSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(JuniperSpacing.lg),
                ) {
                    items(visibleMessages) { bubble -> MessageBubble(bubble) }
                }
            }
        }
    }
}

/** Composer field with no outline or fill, so it reads as part of the pill around it. */
@Composable
private fun composerFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/** Empty chat: one centred headline on the page, with no decoration. */
@Composable
fun EmptyState(text: String = "Ask the local model anything.", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(JuniperSpacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val colors = MaterialTheme.colorScheme
    val containerColor = when {
        bubble.isError -> colors.errorContainer
        bubble.isUser -> colors.primaryContainer
        else -> colors.surface
    }
    val contentColor = when {
        bubble.isError -> colors.onErrorContainer
        bubble.isUser -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource),
        horizontalArrangement = if (bubble.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // The copy action sits in a fixed slot beside the bubble, so showing it never shifts the text.
        val copySlot: @Composable () -> Unit = {
            Box(modifier = Modifier.size(JuniperSpacing.actionSlot), contentAlignment = Alignment.Center) {
                if (hovered && bubble.content.isNotEmpty()) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(bubble.content)) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_copy),
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (bubble.isUser) copySlot()
        Box(modifier = Modifier.widthIn(max = JuniperSpacing.bubbleMaxWidth)) {
            if (bubble.isUser || bubble.isError) {
                // The user's own words and errors sit in a tonal bubble.
                Surface(
                    color = containerColor,
                    contentColor = contentColor,
                    shape = MaterialTheme.shapes.large,
                ) {
                    SelectionContainer {
                        Text(
                            text = bubble.content.ifEmpty { "…" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = JuniperSpacing.lg, vertical = JuniperSpacing.md),
                        )
                    }
                }
            } else {
                // Model replies are plain reading text on the page, not a boxed card.
                SelectionContainer {
                    MarkdownContent(
                        source = bubble.content.ifEmpty { "…" },
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = JuniperSpacing.xs, vertical = JuniperSpacing.xs),
                    )
                }
            }
        }
        if (!bubble.isUser) copySlot()
    }
}
