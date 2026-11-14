package raju.shingadiya.juniper.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import raju.shingadiya.juniper.resources.Res
import raju.shingadiya.juniper.resources.ic_settings
import raju.shingadiya.juniper.ui.JuniperSpacing

/**
 * Left sidebar: the chat list, a "New chat" button and the theme toggle.
 * Click a chat to open it. Right-click a chat for Rename and Delete.
 */
@Composable
fun ChatSidebar(
    chats: List<ChatSummary>,
    activeChatId: String?,
    darkTheme: Boolean,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (chatId: String, title: String) -> Unit,
    onDelete: (String) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<ChatSummary?>(null) }
    var deleting by remember { mutableStateOf<ChatSummary?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.width(JuniperSpacing.sidebarWidth).fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(JuniperSpacing.md)) {
            Text(
                text = "Juniper",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = JuniperSpacing.sm, vertical = JuniperSpacing.xs),
            )
            Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
                Text("New chat")
            }
            Spacer(Modifier.height(JuniperSpacing.xs))
            if (chats.isEmpty()) {
                Text(
                    text = "No chats yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(JuniperSpacing.sm),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(JuniperSpacing.xs),
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatListItem(
                        chat = chat,
                        selected = chat.id == activeChatId,
                        onSelect = { onSelect(chat.id) },
                        onRename = { renaming = chat },
                        onDelete = { deleting = chat },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = JuniperSpacing.sm))
            IconButton(onClick = { settingsOpen = true }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (settingsOpen) {
        SettingsDialog(
            darkTheme = darkTheme,
            onDarkThemeChange = onDarkThemeChange,
            onDismiss = { settingsOpen = false },
        )
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
                    Text("Delete")
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
private fun SettingsDialog(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Dark theme", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (darkTheme) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
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

    Box {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (selected) colors.primaryContainer else colors.surface,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onClick(matcher = PointerMatcher.mouse(PointerButton.Primary), onClick = onSelect)
                .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = { menuOpen = true }),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = JuniperSpacing.md, vertical = JuniperSpacing.sm),
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
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                text = { Text("Delete") },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}
