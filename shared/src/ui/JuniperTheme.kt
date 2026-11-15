package raju.shingadiya.juniper.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Corner radii: large, friendly containment. Cards, bubbles and the composer all draw from this.
 */
val JuniperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Type scale. Headlines are bolder and tighter for presence; body text keeps Material's default
 * reading size and line height so long answers stay comfortable to read.
 */
val JuniperTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

/** Shared spacing and size tokens. Screens use these instead of raw dp values. */
object JuniperSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val bubbleMaxWidth: Dp = 680.dp
    val sidebarWidth: Dp = 292.dp
    val settingsMaxWidth: Dp = 460.dp
    val swatchTile: Dp = 96.dp
    /** Width reserved beside a message for its copy action, so showing it never shifts the text. */
    val actionSlot: Dp = 40.dp
    val listIndent: Dp = 16.dp
    val listMarkerWidth: Dp = 28.dp
}

/**
 * Builds the full Material 3 colour scheme for [seedArgb] in light or dark mode, so the whole app
 * recolours from one accent choice. Tones come from Material's tonal-spot palette generator.
 */
fun seedColorScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val roles = SeedRoles(seedArgb, dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
        inversePrimary = roles.inversePrimary,
        secondary = roles.secondary,
        onSecondary = roles.onSecondary,
        secondaryContainer = roles.secondaryContainer,
        onSecondaryContainer = roles.onSecondaryContainer,
        tertiary = roles.tertiary,
        onTertiary = roles.onTertiary,
        tertiaryContainer = roles.tertiaryContainer,
        onTertiaryContainer = roles.onTertiaryContainer,
        background = roles.background,
        onBackground = roles.onBackground,
        surface = roles.surface,
        onSurface = roles.onSurface,
        surfaceVariant = roles.surfaceVariant,
        onSurfaceVariant = roles.onSurfaceVariant,
        surfaceTint = roles.primary,
        surfaceBright = roles.surfaceBright,
        surfaceDim = roles.surfaceDim,
        surfaceContainerLowest = roles.surfaceContainerLowest,
        surfaceContainerLow = roles.surfaceContainerLow,
        surfaceContainer = roles.surfaceContainer,
        surfaceContainerHigh = roles.surfaceContainerHigh,
        surfaceContainerHighest = roles.surfaceContainerHighest,
        outline = roles.outline,
        outlineVariant = roles.outlineVariant,
        inverseSurface = roles.inverseSurface,
        inverseOnSurface = roles.inverseOnSurface,
        error = roles.error,
        onError = roles.onError,
        errorContainer = roles.errorContainer,
        onErrorContainer = roles.onErrorContainer,
    )
}

/**
 * The single app theme. [darkTheme] picks the light or dark tones, and [seedArgb] is the accent
 * every colour is generated from. Colours, typography and shapes come from [MaterialTheme];
 * change them here, not in individual screens.
 */
@Composable
fun JuniperTheme(darkTheme: Boolean, seedArgb: Int, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = seedColorScheme(seedArgb, darkTheme),
        typography = JuniperTypography,
        shapes = JuniperShapes,
        content = content,
    )
}
