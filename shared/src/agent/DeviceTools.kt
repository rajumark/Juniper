package raju.shingadiya.juniper.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import raju.shingadiya.juniper.adb.AdbClient
import raju.shingadiya.juniper.adb.UiNode

/** Shown to the model when a tool runs but no device is selected. A normal result, not an exception. */
internal const val NO_DEVICE_MESSAGE = "No Android device is selected. Ask the user to connect one with USB debugging on and select it in the app."

/** Arguments for tools that take none. */
@Serializable
internal class NoArgs

/**
 * Read-only device tools. Each one runs a fixed adb command and returns a small JSON result, so the
 * model explains facts instead of guessing shell commands.
 */
internal fun readOnlyDeviceTools(adb: AdbClient, selectedSerial: () -> String?): List<ToolBase<*, *>> = listOf(
    AndroidVersionTool(adb, selectedSerial),
    DeviceInfoTool(adb, selectedSerial),
    BatteryInfoTool(adb, selectedSerial),
    ScreenInfoTool(adb, selectedSerial),
    StorageInfoTool(adb, selectedSerial),
    DumpScreenTool(adb, selectedSerial),
)

/**
 * Tools that change the device. They are implemented, but deliberately NOT registered with the
 * agent for now. To enable one, add it to the registry in [raju.shingadiya.juniper.agent.KoogChatAgent].
 */
internal fun deviceChangingTools(adb: AdbClient, selectedSerial: () -> String?): List<ToolBase<*, *>> = listOf(
    TapTool(adb, selectedSerial),
    SwipeTool(adb, selectedSerial),
    InputTextTool(adb, selectedSerial),
    PressKeyTool(adb, selectedSerial),
    StartActivityTool(adb, selectedSerial),
)

private suspend fun shell(adb: AdbClient, serial: String, vararg argv: String): String =
    adb.runShell(serial, argv.toList()).trim()

private fun success(vararg fields: Pair<String, Any?>): String = buildJsonObject {
    put("status", "success")
    for ((key, value) in fields) {
        when (value) {
            null -> {}
            is Number -> put(key, value)
            is Boolean -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}.toString()

private suspend inline fun withDevice(selectedSerial: () -> String?, block: (String) -> String): String {
    val serial = selectedSerial() ?: return NO_DEVICE_MESSAGE
    return block(serial)
}

internal class AndroidVersionTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<NoArgs>(
    argsType = typeToken<NoArgs>(),
    name = "get_android_version",
    description = "Returns the Android version (release) and API level (sdk) of the selected device.",
) {
    override suspend fun execute(args: NoArgs): String = withDevice(selectedSerial) { serial ->
        val release = shell(adb, serial, "getprop", "ro.build.version.release")
        val sdk = shell(adb, serial, "getprop", "ro.build.version.sdk")
        success("release" to release, "sdk" to (sdk.toIntOrNull() ?: sdk))
    }
}

internal class DeviceInfoTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<NoArgs>(
    argsType = typeToken<NoArgs>(),
    name = "get_device_info",
    description = "Returns the manufacturer, brand, model and device codename of the selected device.",
) {
    override suspend fun execute(args: NoArgs): String = withDevice(selectedSerial) { serial ->
        success(
            "manufacturer" to shell(adb, serial, "getprop", "ro.product.manufacturer"),
            "brand" to shell(adb, serial, "getprop", "ro.product.brand"),
            "model" to shell(adb, serial, "getprop", "ro.product.model"),
            "device" to shell(adb, serial, "getprop", "ro.product.device"),
        )
    }
}

internal class BatteryInfoTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<NoArgs>(
    argsType = typeToken<NoArgs>(),
    name = "get_battery_info",
    description = "Returns the battery level in percent, temperature in Celsius and charging status of the selected device.",
) {
    override suspend fun execute(args: NoArgs): String = withDevice(selectedSerial) { serial ->
        val output = shell(adb, serial, "dumpsys", "battery")
        val values = output.lines().mapNotNull { line ->
            val parts = line.trim().split(":", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
        val level = values["level"]?.toIntOrNull()
        val tenthsCelsius = values["temperature"]?.toIntOrNull()
        success(
            "level_percent" to level,
            "temperature_celsius" to tenthsCelsius?.let { it / 10.0 },
            "charging" to (values["status"]?.toIntOrNull()?.let { it == 2 || it == 5 }),
        )
    }
}

internal class ScreenInfoTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<NoArgs>(
    argsType = typeToken<NoArgs>(),
    name = "get_screen_info",
    description = "Returns the screen resolution (physical size in pixels) and density (dpi) of the selected device.",
) {
    override suspend fun execute(args: NoArgs): String = withDevice(selectedSerial) { serial ->
        val size = shell(adb, serial, "wm", "size")
        val density = shell(adb, serial, "wm", "density")
        success(
            "resolution" to size.substringAfter(":", "").trim().ifEmpty { size },
            "density_dpi" to density.substringAfter(":", "").trim().toIntOrNull(),
        )
    }
}

internal class StorageInfoTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<NoArgs>(
    argsType = typeToken<NoArgs>(),
    name = "get_storage_info",
    description = "Returns total, used and available storage of the device's data partition.",
) {
    override suspend fun execute(args: NoArgs): String = withDevice(selectedSerial) { serial ->
        success("df_output" to shell(adb, serial, "df", "/data"))
    }
}

internal class DumpScreenTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<NoArgs>(
    argsType = typeToken<NoArgs>(),
    name = "dump_screen",
    description = "Lists the visible text and clickable elements on the device screen with their center coordinates. " +
        "Call this before tapping so you know where things are.",
) {
    override suspend fun execute(args: NoArgs): String {
        val serial = selectedSerial() ?: return NO_DEVICE_MESSAGE
        return formatScreen(adb.dumpUiHierarchy(serial))
    }
}

/** One line per element that has text, a description or can be clicked. */
internal fun formatScreen(nodes: List<UiNode>): String {
    val lines = nodes
        .filter { it.text.isNotEmpty() || it.contentDescription.isNotEmpty() || it.clickable }
        .take(MAX_SCREEN_ELEMENTS)
        .map { node ->
            buildString {
                append("(${node.bounds.centerX}, ${node.bounds.centerY}) ")
                if (node.clickable) append("[clickable] ")
                if (node.text.isNotEmpty()) append("text=\"${node.text}\" ")
                if (node.contentDescription.isNotEmpty()) append("desc=\"${node.contentDescription}\"")
            }.trimEnd()
        }
    return if (lines.isEmpty()) "No text or clickable elements are visible." else lines.joinToString("\n")
}

private const val MAX_SCREEN_ELEMENTS = 150

// ---- Device-changing tools (not registered) ----

@Serializable
internal data class TapArgs(
    @property:LLMDescription("Horizontal pixel position")
    val x: Int,
    @property:LLMDescription("Vertical pixel position")
    val y: Int,
)

internal class TapTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<TapArgs>(
    argsType = typeToken<TapArgs>(),
    name = "tap",
    description = "Taps the device screen at pixel coordinates.",
) {
    override suspend fun execute(args: TapArgs): String = withDevice(selectedSerial) { serial ->
        adb.tap(serial, args.x, args.y)
        "Tapped."
    }
}

@Serializable
internal data class SwipeArgs(
    @property:LLMDescription("Start x") val x1: Int,
    @property:LLMDescription("Start y") val y1: Int,
    @property:LLMDescription("End x") val x2: Int,
    @property:LLMDescription("End y") val y2: Int,
    @property:LLMDescription("Swipe duration in milliseconds, default 300") val durationMs: Int = 300,
)

internal class SwipeTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<SwipeArgs>(
    argsType = typeToken<SwipeArgs>(),
    name = "swipe",
    description = "Swipes from (x1, y1) to (x2, y2). Use it to scroll: swipe up to see content below.",
) {
    override suspend fun execute(args: SwipeArgs): String = withDevice(selectedSerial) { serial ->
        adb.swipe(serial, args.x1, args.y1, args.x2, args.y2, args.durationMs)
        "Swiped."
    }
}

@Serializable
internal data class InputTextArgs(
    @property:LLMDescription("Text to type") val text: String,
)

internal class InputTextTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<InputTextArgs>(
    argsType = typeToken<InputTextArgs>(),
    name = "input_text",
    description = "Types text into the focused text field on the device.",
) {
    override suspend fun execute(args: InputTextArgs): String = withDevice(selectedSerial) { serial ->
        adb.inputText(serial, args.text)
        "Typed."
    }
}

@Serializable
internal data class PressKeyArgs(
    @property:LLMDescription("Android key code name, for example KEYCODE_BACK") val keyCode: String,
)

internal class PressKeyTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<PressKeyArgs>(
    argsType = typeToken<PressKeyArgs>(),
    name = "press_key",
    description = "Presses an Android key, for example KEYCODE_BACK, KEYCODE_HOME or KEYCODE_ENTER.",
) {
    override suspend fun execute(args: PressKeyArgs): String = withDevice(selectedSerial) { serial ->
        adb.keyEvent(serial, args.keyCode)
        "Key pressed."
    }
}

@Serializable
internal data class StartActivityArgs(
    @property:LLMDescription("Component name: package/activity") val component: String,
)

internal class StartActivityTool(
    private val adb: AdbClient,
    private val selectedSerial: () -> String?,
) : SimpleTool<StartActivityArgs>(
    argsType = typeToken<StartActivityArgs>(),
    name = "start_activity",
    description = "Opens an app screen by component name, for example com.android.settings/.Settings.",
) {
    override suspend fun execute(args: StartActivityArgs): String = withDevice(selectedSerial) { serial ->
        adb.startActivity(serial, args.component)
        "Activity started."
    }
}
