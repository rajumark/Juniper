package raju.shingadiya.juniper.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import raju.shingadiya.juniper.ui.JuniperSpacing
import raju.shingadiya.juniper.ui.SeedRoles

/**
 * Settings page: how the app is lit (Light, Dark or follow the device) and which accent colour
 * every palette is built from. Every change is passed to [onChange] right away, so it is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingCustom by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JuniperSpacing.xl, vertical = JuniperSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = JuniperSpacing.settingsMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(JuniperSpacing.md),
            ) {
                SectionTitle("Theme")
                ThemeModeRow(
                    selected = settings.themeMode,
                    onSelect = { onChange(settings.copy(themeMode = it)) },
                )

                Spacer(Modifier.height(JuniperSpacing.md))
                SectionTitle("Accent colour")
                SeedGrid(
                    settings = settings,
                    onSelectPreset = { onChange(settings.copy(seedArgb = it)) },
                    onOpenCustom = { pickingCustom = true },
                )
            }
        }
    }

    if (pickingCustom) {
        CustomColorDialog(
            initialArgb = settings.customArgb,
            onConfirm = { argb ->
                onChange(settings.copy(seedArgb = argb, customArgb = argb))
                pickingCustom = false
            },
            onDismiss = { pickingCustom = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = JuniperSpacing.xs),
    )
}

/** Light / Dark / Device as one segmented control. The selected segment shows a check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            val isSelected = mode == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {
                    when {
                        isSelected -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        mode == ThemeMode.Light -> Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        mode == ThemeMode.Dark -> Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                label = { Text(mode.label()) },
            )
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
    ThemeMode.Device -> "Device"
}

/** Four tiles per row: the presets, then the custom picker in the last slot. */
@Composable
private fun SeedGrid(
    settings: AppSettings,
    onSelectPreset: (Int) -> Unit,
    onOpenCustom: () -> Unit,
) {
    val tiles = SeedPalette.presets + listOf(CUSTOM_TILE)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val rows = tiles.chunked(4)

    Column(verticalArrangement = Arrangement.spacedBy(JuniperSpacing.md)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(JuniperSpacing.md)) {
                row.forEach { argb ->
                    val isCustom = argb == CUSTOM_TILE
                    val selected = if (isCustom) settings.seedArgb == settings.customArgb && !SeedPalette.presets.contains(settings.seedArgb)
                    else settings.seedArgb == argb
                    SeedTile(
                        argb = if (isCustom) settings.customArgb else argb,
                        isCustom = isCustom,
                        dark = dark,
                        selected = selected,
                        onClick = if (isCustom) onOpenCustom else ({ onSelectPreset(argb) }),
                    )
                }
                // Keep a short last row aligned with the grid above it.
                repeat(4 - row.size) { Spacer(Modifier.size(JuniperSpacing.swatchTile)) }
            }
        }
    }
}

/** A sentinel in the grid for the custom tile; never stored as a colour. */
private const val CUSTOM_TILE = 0

@Composable
private fun SeedTile(
    argb: Int,
    isCustom: Boolean,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = remember(argb, dark) { SeedRoles(argb, dark) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(JuniperSpacing.swatchTile),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isCustom) {
                // The custom tile shows the picked colour with an eyedropper on top.
                val fill = Color(argb)
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                        .background(fill, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Colorize,
                        contentDescription = "Pick a custom colour",
                        tint = if (fill.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            } else {
                // Top half: primary. Bottom: primary container and the neutral tone.
                Column(
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                        .clip(CircleShape),
                ) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(roles.swatchTop))
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(roles.swatchBottomLeft))
                        Box(Modifier.weight(1f).fillMaxHeight().background(roles.swatchBottomRight))
                    }
                }
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(JuniperSpacing.xs)
                        .size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom accent colour: hue, saturation and brightness sliders with a live preview. Stored as an
 * opaque ARGB value.
 */
@Composable
private fun CustomColorDialog(
    initialArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hsb = remember(initialArgb) {
        val r = (initialArgb shr 16) and 0xFF
        val g = (initialArgb shr 8) and 0xFF
        val b = initialArgb and 0xFF
        java.awt.Color.RGBtoHSB(r, g, b, null)
    }
    var hue by remember { mutableStateOf(hsb[0] * 360f) }
    var saturation by remember { mutableStateOf(hsb[1]) }
    var brightness by remember { mutableStateOf(hsb[2]) }

    val argb = java.awt.Color.HSBtoRGB(hue / 360f, saturation, brightness) or (0xFF shl 24)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(JuniperSpacing.md)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(argb), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "#%06X".format(argb and 0xFFFFFF),
                        color = if (Color(argb).luminance() > 0.5f) Color.Black else Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
                SliderRow("Hue", hue, 0f..360f) { hue = it }
                SliderRow("Saturation", saturation * 100f, 0f..100f) { saturation = it / 100f }
                SliderRow("Brightness", brightness * 100f, 0f..100f) { brightness = it / 100f }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(argb) }) { Text("Use colour") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${value.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
