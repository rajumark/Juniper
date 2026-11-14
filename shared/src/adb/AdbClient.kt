package raju.shingadiya.juniper.adb

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Thin wrapper around the `adb` command line. Every method runs one adb command for one device
 * and throws [AdbException] if it fails. Device serials are passed explicitly with `-s`, so
 * the same app can drive several devices.
 */
class AdbClient(
    private val adbPath: String = locateAdb(),
    private val runner: CommandRunner = ProcessCommandRunner(),
    private val timeout: Duration = 30.seconds,
) {

    /** Lists all devices known to the adb server, including ones that are not ready. */
    suspend fun devices(): List<AdbDevice> =
        parseDevices(run("devices", "-l").stdout)

    /** Taps the screen at pixel coordinates ([x], [y]). */
    suspend fun tap(serial: String, x: Int, y: Int) {
        shell(serial, "input", "tap", x.toString(), y.toString())
    }

    /** Swipes from ([x1], [y1]) to ([x2], [y2]) over [durationMs] milliseconds. */
    suspend fun swipe(serial: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300) {
        shell(serial, "input", "swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMs.toString())
    }

    /** Types [text] into the focused field. Spaces and shell characters are escaped for the device. */
    suspend fun inputText(serial: String, text: String) {
        shell(serial, "input", "text", escapeInputText(text))
    }

    /** Sends a key event, for example `KEYCODE_BACK`, `KEYCODE_HOME` or `KEYCODE_ENTER`. */
    suspend fun keyEvent(serial: String, keyCode: String) {
        shell(serial, "input", "keyevent", keyCode)
    }

    /** Starts an activity by component name, for example `com.android.settings/.Settings`. */
    suspend fun startActivity(serial: String, component: String): String =
        shell(serial, "am", "start", "-n", component)

    /** Captures the current screen's UI hierarchy as a flat list of elements. */
    suspend fun dumpUiHierarchy(serial: String): List<UiNode> {
        shell(serial, "uiautomator", "dump", REMOTE_DUMP_PATH)
        val xml = shell(serial, "cat", REMOTE_DUMP_PATH)
        return parseUiHierarchy(xml)
    }

    private suspend fun shell(serial: String, vararg command: String): String {
        require(serial.isNotBlank()) { "Device serial must not be blank" }
        return run("-s", serial, "shell", *command).stdout
    }

    private suspend fun run(vararg args: String): CommandResult {
        val result = runner.run(listOf(adbPath) + args, timeout)
        if (result.exitCode != 0) {
            val detail = result.stderr.ifBlank { result.stdout }.trim()
            throw AdbException("adb ${args.joinToString(" ")} failed (exit ${result.exitCode}): $detail")
        }
        return result
    }

    companion object {
        const val REMOTE_DUMP_PATH = "/sdcard/juniper_window_dump.xml"

        /**
         * Parses the output of `adb devices -l`:
         * ```
         * List of devices attached
         * emulator-5554  device product:sdk_gphone model:sdk_gphone device:emu64 transport_id:1
         * ```
         */
        fun parseDevices(output: String): List<AdbDevice> =
            output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") }
                .map { line ->
                    val parts = line.split(Regex("\\s+"))
                    val properties = parts.drop(2)
                        .mapNotNull { token ->
                            val index = token.indexOf(':')
                            if (index > 0) token.substring(0, index) to token.substring(index + 1) else null
                        }
                        .toMap()
                    AdbDevice(serial = parts[0], state = parts.getOrElse(1) { "unknown" }, properties = properties)
                }
                .toList()

        /**
         * Escapes [text] for `adb shell input text`. The device shell splits arguments on spaces
         * and interprets `%s` as a space, so spaces become `%s` and other non-alphanumeric
         * characters get a backslash.
         */
        fun escapeInputText(text: String): String = buildString {
            for (ch in text) {
                when {
                    ch == ' ' -> append("%s")
                    ch == '%' -> append("\\%")
                    ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' -> append(ch)
                    ch in "-_.,@:/+=" -> append(ch)
                    else -> {
                        append('\\')
                        append(ch)
                    }
                }
            }
        }

        /**
         * Parses the XML written by `uiautomator dump`. The command can print a banner before the
         * XML, so parsing starts at the `<?xml` declaration.
         */
        fun parseUiHierarchy(output: String): List<UiNode> {
            val start = output.indexOf("<?xml")
            if (start < 0) throw AdbException("No UI hierarchy found in uiautomator output")
            val factory = DocumentBuilderFactory.newInstance().apply {
                // Refuse DOCTYPE declarations so untrusted XML cannot pull in external entities.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isNamespaceAware = false
            }
            val document = try {
                factory.newDocumentBuilder().parse(InputSource(StringReader(output.substring(start))))
            } catch (e: Exception) {
                throw AdbException("Could not parse uiautomator XML: ${e.message}", e)
            }
            val nodes = mutableListOf<UiNode>()
            collectNodes(document.documentElement, nodes)
            return nodes
        }

        private fun collectNodes(element: Element, into: MutableList<UiNode>) {
            if (element.tagName == "node") {
                parseBounds(element.getAttribute("bounds"))?.let { bounds ->
                    into += UiNode(
                        className = element.getAttribute("class"),
                        text = element.getAttribute("text"),
                        contentDescription = element.getAttribute("content-desc"),
                        resourceId = element.getAttribute("resource-id"),
                        clickable = element.getAttribute("clickable") == "true",
                        enabled = element.getAttribute("enabled") == "true",
                        bounds = bounds,
                    )
                }
            }
            val children = element.childNodes
            for (i in 0 until children.length) {
                (children.item(i) as? Element)?.let { collectNodes(it, into) }
            }
        }

        private val boundsRegex = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

        /** Parses a bounds string like `[0,96][1080,2400]`. Returns null if it does not match. */
        fun parseBounds(value: String): Bounds? {
            val match = boundsRegex.matchEntire(value.trim()) ?: return null
            val (l, t, r, b) = match.destructured
            return Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
        }
    }
}
