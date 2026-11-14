package raju.shingadiya.juniper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing and size tokens. Screens use these instead of raw dp values. */
object JuniperSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val bubbleMaxWidth: Dp = 600.dp
}

/**
 * The single app theme. Colors, typography and shapes come from Material 3 defaults via
 * [MaterialTheme]; change them here, not in individual screens.
 */
@Composable
fun JuniperTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
