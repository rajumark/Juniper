package raju.shingadiya.juniper.ui

import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/**
 * Every Material 3 colour role for one seed colour in one mode. Built once per (seed, mode) by the
 * tonal-spot scheme, then read role by role into Compose [Color]s.
 */
internal class SeedRoles(seedArgb: Int, dark: Boolean) {
    private val scheme = SchemeTonalSpot(Hct.fromInt(seedArgb), dark, 0.0)
    private val dynamic = MaterialDynamicColors()

    private fun Color(role: DynamicColor): Color = Color(role.getArgb(scheme))

    val primary = Color(dynamic.primary())
    val onPrimary = Color(dynamic.onPrimary())
    val primaryContainer = Color(dynamic.primaryContainer())
    val onPrimaryContainer = Color(dynamic.onPrimaryContainer())
    val inversePrimary = Color(dynamic.inversePrimary())
    val secondary = Color(dynamic.secondary())
    val onSecondary = Color(dynamic.onSecondary())
    val secondaryContainer = Color(dynamic.secondaryContainer())
    val onSecondaryContainer = Color(dynamic.onSecondaryContainer())
    val tertiary = Color(dynamic.tertiary())
    val onTertiary = Color(dynamic.onTertiary())
    val tertiaryContainer = Color(dynamic.tertiaryContainer())
    val onTertiaryContainer = Color(dynamic.onTertiaryContainer())
    val background = Color(dynamic.background())
    val onBackground = Color(dynamic.onBackground())
    val surface = Color(dynamic.surface())
    val onSurface = Color(dynamic.onSurface())
    val surfaceVariant = Color(dynamic.surfaceVariant())
    val onSurfaceVariant = Color(dynamic.onSurfaceVariant())
    val surfaceBright = Color(dynamic.surfaceBright())
    val surfaceDim = Color(dynamic.surfaceDim())
    val surfaceContainerLowest = Color(dynamic.surfaceContainerLowest())
    val surfaceContainerLow = Color(dynamic.surfaceContainerLow())
    val surfaceContainer = Color(dynamic.surfaceContainer())
    val surfaceContainerHigh = Color(dynamic.surfaceContainerHigh())
    val surfaceContainerHighest = Color(dynamic.surfaceContainerHighest())
    val outline = Color(dynamic.outline())
    val outlineVariant = Color(dynamic.outlineVariant())
    val inverseSurface = Color(dynamic.inverseSurface())
    val inverseOnSurface = Color(dynamic.inverseOnSurface())
    val error = Color(dynamic.error())
    val onError = Color(dynamic.onError())
    val errorContainer = Color(dynamic.errorContainer())
    val onErrorContainer = Color(dynamic.onErrorContainer())

    /** The three tones a settings tile shows: primary, primary container and the neutral. */
    val swatchTop: Color get() = primary
    val swatchBottomLeft: Color get() = primaryContainer
    val swatchBottomRight: Color get() = surfaceVariant
}
