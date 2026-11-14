package raju.shingadiya.juniper.adb

/** A device listed by `adb devices -l`. [state] is "device" when ready, or e.g. "unauthorized" or "offline". */
data class AdbDevice(
    val serial: String,
    val state: String,
    val properties: Map<String, String>,
) {
    val isReady: Boolean get() = state == "device"
}

/** Pixel rectangle of a UI element, as reported by UI Automator. */
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/** One element from a UI Automator hierarchy dump. Empty strings mean the attribute was absent. */
data class UiNode(
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val clickable: Boolean,
    val enabled: Boolean,
    val bounds: Bounds,
)

/** Raw result of running an external command. */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** Raised when adb cannot run, times out, or exits with a non-zero code. */
class AdbException(message: String, cause: Throwable? = null) : Exception(message, cause)
