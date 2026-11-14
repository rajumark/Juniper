package raju.shingadiya.juniper.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import raju.shingadiya.juniper.device.DeviceInfo
import raju.shingadiya.juniper.device.DeviceSnapshot
import raju.shingadiya.juniper.model.ModelManager
import raju.shingadiya.juniper.model.ModelSnapshot
import raju.shingadiya.juniper.ui.JuniperSpacing

private const val OLLAMA_DOWNLOAD_URL = "https://ollama.com/download"

/**
 * Model and device chips shown under the input box. Clicking a chip opens its menu; the model chip
 * opens a setup guide instead when no usable model is selected.
 */
@Composable
fun SelectorBar(
    selectedModel: String,
    models: ModelSnapshot,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    devices: DeviceSnapshot,
    onSelectDevice: (String) -> Unit,
    onRefreshDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(JuniperSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelChip(
            selected = selectedModel,
            models = models,
            onSelect = onSelectModel,
            onRefresh = onRefreshModels,
        )
        DeviceChip(
            devices = devices,
            onSelect = onSelectDevice,
            onRefresh = onRefreshDevices,
        )
    }
}

@Composable
private fun ModelChip(
    selected: String,
    models: ModelSnapshot,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }

    val hasUsableSelection = models.ollamaReachable == true && models.isInstalled(selected)
    val label = when {
        models.ollamaReachable == null -> "Checking Ollama…"
        models.ollamaReachable == false -> "Ollama not running"
        selected.isBlank() -> "Select model"
        !models.isInstalled(selected) -> "$selected (not installed)"
        else -> selected
    }

    Box {
        AssistChip(
            onClick = {
                // No usable model yet: explain what to install instead of opening an empty menu.
                if (hasUsableSelection) menuOpen = true else guideOpen = true
            },
            label = { Text("Model: $label ▾") },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            models.installed.forEach { name ->
                DropdownMenuItem(
                    text = { MenuLabel(name, if (name == selected) "Selected" else null) },
                    onClick = {
                        onSelect(name)
                        menuOpen = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Setup guide") },
                onClick = {
                    menuOpen = false
                    guideOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text(if (models.isRefreshing) "Refreshing…" else "Refresh") },
                enabled = !models.isRefreshing,
                onClick = onRefresh,
            )
        }
    }

    if (guideOpen) {
        ModelGuideDialog(
            selected = selected,
            models = models,
            onSelect = {
                onSelect(it)
                guideOpen = false
            },
            onRefresh = onRefresh,
            onDismiss = { guideOpen = false },
        )
    }
}

@Composable
private fun ModelGuideDialog(
    selected: String,
    models: ModelSnapshot,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    // LocalClipboardManager is deprecated in favor of a suspend API that this Compose version lacks
    // helpers for, so the synchronous one is kept here on purpose.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val suggested = ModelManager.SUGGESTED_MODEL
    val pullCommand = "ollama pull $suggested"
    val suggestedInstalled = models.isInstalled(suggested)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up a local model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(JuniperSpacing.sm)) {
                StepText(
                    done = models.ollamaReachable == true,
                    title = "1. Install and start Ollama",
                    detail = when (models.ollamaReachable) {
                        true -> "Ollama is running on this computer."
                        false -> "Ollama is not running. Install it from ollama.com, open it, then press Refresh."
                        null -> "Checking…"
                    },
                )
                TextButton(onClick = { uriHandler.openUri(OLLAMA_DOWNLOAD_URL) }) {
                    Text("Open ollama.com")
                }

                StepText(
                    done = suggestedInstalled,
                    title = "2. Download the suggested model",
                    detail = "Juniper is tested with $suggested. Run this in a terminal:",
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pullCommand,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(JuniperSpacing.sm).weight(1f),
                        )
                        TextButton(onClick = { clipboard.setText(AnnotatedString(pullCommand)) }) {
                            Text("Copy")
                        }
                    }
                }

                if (models.installed.isNotEmpty()) {
                    StepText(
                        done = true,
                        title = "Installed models",
                        detail = "Choose one to use in this chat:",
                    )
                    models.installed.forEach { name ->
                        TextButton(onClick = { onSelect(name) }) {
                            Text(if (name == selected) "$name (selected)" else name)
                        }
                    }
                }

                models.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !models.isRefreshing) {
                Text(if (models.isRefreshing) "Checking…" else "Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** Title with an optional smaller line underneath, for use inside a menu item. */
@Composable
private fun MenuLabel(title: String, detail: String?) {
    Column {
        Text(text = title)
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepText(done: Boolean, title: String, detail: String) {
    Column {
        Text(
            text = (if (done) "✓ " else "• ") + title,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(text = detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeviceChip(
    devices: DeviceSnapshot,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = devices.selected

    val label = when {
        !devices.hasLoaded -> "Checking devices…"
        devices.adbMissing -> "adb not found"
        devices.error != null -> "Device check failed"
        devices.devices.isEmpty() -> "No device"
        selected == null -> "No ready device"
        !selected.isReady -> "${selected.displayName} (${selected.status.name.lowercase()})"
        else -> selected.displayName
    }

    Box {
        AssistChip(
            onClick = { menuOpen = true },
            label = { Text("Device: $label ▾") },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            when {
                devices.adbMissing || devices.error != null -> {
                    DropdownMenuItem(
                        text = {
                            MenuLabel(
                                title = devices.error ?: "adb not found",
                                detail = "Install Android platform-tools, or set ANDROID_HOME to your SDK folder.",
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }
                devices.devices.isEmpty() -> {
                    DropdownMenuItem(
                        text = {
                            MenuLabel(
                                title = "No device connected",
                                detail = "Connect a phone with USB debugging on, or start an emulator.",
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }
                else -> {
                    devices.devices.forEach { device ->
                        DeviceItem(
                            device = device,
                            isSelected = device.serial == devices.selectedSerial,
                            onClick = {
                                onSelect(device.serial)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (devices.isRefreshing) "Refreshing…" else "Refresh") },
                enabled = !devices.isRefreshing,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun DeviceItem(device: DeviceInfo, isSelected: Boolean, onClick: () -> Unit) {
    val kind = if (device.isEmulator) "emulator" else "device"
    DropdownMenuItem(
        text = {
            MenuLabel(
                title = device.displayName,
                detail = "$kind · ${device.serial}" + if (isSelected) " · selected" else "",
            )
        },
        enabled = device.isReady,
        onClick = onClick,
    )
    // Explain why a device cannot be picked, directly under it.
    if (!device.isReady) {
        Text(
            text = device.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = JuniperSpacing.lg),
        )
    }
}
