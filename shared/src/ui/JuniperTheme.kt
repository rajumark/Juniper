package raju.shingadiya.juniper.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Light and dark color schemes. Change app colors here, not in individual screens. */
val JuniperLightColors: ColorScheme = lightColorScheme()
val JuniperDarkColors: ColorScheme = darkColorScheme()

/** Shared spacing and size tokens. Screens use these instead of raw dp values. */
object JuniperSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val bubbleMaxWidth: Dp = 600.dp
    val sidebarWidth: Dp = 280.dp
    val listIndent: Dp = 16.dp
    val listMarkerWidth: Dp = 28.dp
}

/**
 * The single app theme. Pass [darkTheme] to switch between [JuniperLightColors] and
 * [JuniperDarkColors]. Colors, typography and shapes come from [MaterialTheme]; change them here,
 * not in individual screens.
 */
@Composable
fun JuniperTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) JuniperDarkColors else JuniperLightColors,
        content = content,
    )
}
