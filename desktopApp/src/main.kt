package raju.shingadiya.juniper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit

/**
 * Window options from the command line. Nothing is hard-coded: when a value is omitted, the
 * window uses the main display's usable area (screen minus menu bar / taskbar / dock).
 *
 * Usage: `--width=<pixels> --height=<pixels>`
 */
data class WindowOptions(
    val width: Int? = null,
    val height: Int? = null,
) {
    companion object {
        fun parse(args: Array<String>): WindowOptions {
            var width: Int? = null
            var height: Int? = null
            for (arg in args) {
                when {
                    arg.startsWith("--width=") -> width = positiveInt(arg, "--width")
                    arg.startsWith("--height=") -> height = positiveInt(arg, "--height")
                    else -> println("[Juniper] ignoring unknown argument: $arg")
                }
            }
            return WindowOptions(width, height)
        }

        private fun positiveInt(arg: String, flag: String): Int {
            val value = arg.substringAfter("=").toIntOrNull()
            require(value != null && value > 0) { "$flag must be a positive whole number, got '$arg'" }
            return value
        }
    }
}

fun main(args: Array<String>) {
    val options = WindowOptions.parse(args)
    println("[Juniper] window options: $options")
    application {
        // Maximized by default; an explicit --width or --height opens a floating, sized window instead.
        val sizedByFlags = options.width != null || options.height != null
        val state = rememberWindowState(
            placement = if (sizedByFlags) WindowPlacement.Floating else WindowPlacement.Maximized,
        )
        Window(
            onCloseRequest = {
                println("[Juniper] close requested")
                exitApplication()
            },
            state = state,
            title = "Juniper",
        ) {
            FitToMainDisplay(options)
            App()
        }
    }
}

/**
 * Sizes the window from [options]. Any missing width or height falls back to the main display's
 * usable area, and sizes are capped to that area. The window stays a normal window, so the OS
 * maximize / zoom button still works.
 *
 * Uses AWT bounds so it works the same on macOS, Windows and Linux.
 */
@Composable
private fun FrameWindowScope.FitToMainDisplay(options: WindowOptions) {
    LaunchedEffect(window, options) {
        // No flags: WindowPlacement.Maximized handles the size, so there is nothing to set here.
        if (options.width == null && options.height == null) return@LaunchedEffect
        val config = window.graphicsConfiguration ?: return@LaunchedEffect
        val bounds = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val usableX = bounds.x + insets.left
        val usableY = bounds.y + insets.top
        val usableWidth = bounds.width - insets.left - insets.right
        val usableHeight = bounds.height - insets.top - insets.bottom
        val width = minOf(options.width ?: usableWidth, usableWidth)
        val height = minOf(options.height ?: usableHeight, usableHeight)
        window.setBounds(usableX, usableY, width, height)
        println("[Juniper] display bounds=$bounds insets=$insets")
        println("[Juniper] window size set to ${width}x$height at ($usableX,$usableY); actual=${window.size}, visible=${window.isVisible}, showing=${window.isShowing}")
    }
}
