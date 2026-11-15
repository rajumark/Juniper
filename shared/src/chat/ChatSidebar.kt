package raju.shingadiya.juniper.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import raju.shingadiya.juniper.resources.Res
import raju.shingadiya.juniper.resources.ic_more_vert
import raju.shingadiya.juniper.resources.ic_settings
import raju.shingadiya.juniper.ui.JuniperSpacing

/**
 * Left sidebar: brand header, a search field, the chat list, and a footer with settings.
 * Sits on a tonal surface rather than behind a divider. Click a chat to open it; right-click a chat
 * for Rename and Delete.
 */
@Composable
fun ChatSidebar(
    chats: List<ChatSummary>,
    activeChatId: String?,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (chatId: String, title: String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<ChatSummary?>(null) }
    var deleting by remember { mutableStateOf<ChatSummary?>(null) }
    var query by remember { mutableStateOf("") }

    val visibleChats = remember(chats, query) {
        val q = query.trim()
        if (q.isEmpty()) chats else chats.filter { it.title.contains(q, ignoreCase = true) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.width(JuniperSpacing.sidebarWidth).fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = JuniperSpacing.md)) {
            // Brand header: a small mark beside the name, the same lockup EchoFlow uses in its drawer.
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = JuniperSpacing.xs,
                    top = JuniperSpacing.md,
                    bottom = JuniperSpacing.lg,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "J",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(JuniperSpacing.md))
                Text(
                    text = "Juniper",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("New chat")
            }
            Spacer(Modifier.height(JuniperSpacing.md))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search chats") },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(JuniperSpacing.md))

            if (chats.isEmpty()) {
                Text(
                    text = "No chats yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(JuniperSpacing.sm),
                )
            } else if (visibleChats.isEmpty()) {
                Text(
                    text = "Nothing matches “$query”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(JuniperSpacing.sm),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = JuniperSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(JuniperSpacing.xs),
            ) {
                items(visibleChats, key = { it.id }) { chat ->
                    ChatListItem(
                        chat = chat,
                        selected = chat.id == activeChatId,
                        onSelect = { onSelect(chat.id) },
                        onRename = { renaming = chat },
                        onDelete = { deleting = chat },
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = JuniperSpacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = JuniperSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    renaming?.let { chat ->
        RenameChatDialog(
            currentTitle = chat.title,
            onConfirm = { title ->
                onRename(chat.id, title)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${chat.title}\" and all its messages will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(chat.id)
                    deleting = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun RenameChatDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}


/**
 * One chat row. Hovering shows a more (⋮) button at the right; clicking it opens Rename and Delete.
 * Clicking the title selects the chat. Right-click does nothing. The selected chat takes the
 * secondaryContainer tone as a pill; the rest sit flat on the sidebar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: ChatSummary,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .onClick(matcher = PointerMatcher.mouse(PointerButton.Primary), onClick = onSelect)
                    .padding(start = JuniperSpacing.md, top = JuniperSpacing.sm, bottom = JuniperSpacing.sm),
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chat.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A fixed slot keeps the title width the same whether or not the button shows.
            Box(modifier = Modifier.size(JuniperSpacing.actionSlot), contentAlignment = Alignment.Center) {
                if (hovered || menuOpen) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_more_vert),
                            contentDescription = "Chat options",
                            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
