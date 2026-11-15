package raju.shingadiya.juniper.agent

import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.adb.AdbClient
import raju.shingadiya.juniper.adb.CommandResult
import raju.shingadiya.juniper.adb.CommandRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceToolsTest {

    private val recorded = mutableListOf<List<String>>()

    /** Canned adb output, keyed by the last argument of each command (for example `ro.build.version.sdk`). */
    private val answers = mutableMapOf<String, String>()
    private var uiXml = ""

    private val adb = AdbClient(
        adbPath = "adb",
        runner = CommandRunner { command, _ ->
            recorded += command
            val out = if (command.contains("cat")) uiXml else answers[command.last()].orEmpty()
            CommandResult(0, out, "")
        },
    )

    private val serial = { "emulator-5554" }

    @Test
    fun `read-only tools are the registered set and device-changing tools are not`() {
        val registered = readOnlyDeviceTools(adb, serial).map { it.name }
        val deregistered = deviceChangingTools(adb, serial).map { it.name }

        assertEquals(
            listOf("get_android_version", "get_device_info", "get_battery_info", "get_screen_info", "get_storage_info", "dump_screen"),
            registered,
        )
        assertEquals(listOf("tap", "swipe", "input_text", "press_key", "start_activity"), deregistered)
        assertTrue(registered.intersect(deregistered.toSet()).isEmpty())
    }

    @Test
    fun `android version reads release and sdk with adb getprop`() = runTest {
        answers["ro.build.version.release"] = "15"
        answers["ro.build.version.sdk"] = "35"

        val result = AndroidVersionTool(adb, serial).execute(NoArgs())

        assertEquals(listOf("adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.version.release"), recorded[0])
        assertTrue(result.contains("\"release\":\"15\""), result)
        assertTrue(result.contains("\"sdk\":35"), result)
        assertTrue(result.contains("\"status\":\"success\""), result)
    }

    @Test
    fun `battery output is parsed into level, temperature and charging`() = runTest {
        answers["battery"] = "Current Battery Service state:\n  level: 85\n  status: 2\n  temperature: 312"

        val result = BatteryInfoTool(adb, serial).execute(NoArgs())

        assertEquals(listOf("adb", "-s", "emulator-5554", "shell", "dumpsys", "battery"), recorded[0])
        assertTrue(result.contains("\"level_percent\":85"), result)
        assertTrue(result.contains("\"temperature_celsius\":31.2"), result)
        assertTrue(result.contains("\"charging\":true"), result)
    }

    @Test
    fun `no device selected returns a message and runs no adb command`() = runTest {
        val noDevice = { null }

        assertEquals(NO_DEVICE_MESSAGE, AndroidVersionTool(adb, noDevice).execute(NoArgs()))
        assertEquals(NO_DEVICE_MESSAGE, DumpScreenTool(adb, noDevice).execute(NoArgs()))
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `dump_screen lists elements with center coordinates and skips empty ones`() = runTest {
        uiXml = """<?xml version="1.0" encoding="UTF-8" ?>
            <hierarchy>
              <node text="Settings" content-desc="" clickable="true" enabled="true" class="android.widget.TextView" resource-id="" bounds="[0,0][100,50]" />
              <node text="" content-desc="" clickable="false" enabled="true" class="android.view.View" resource-id="" bounds="[0,50][100,100]" />
            </hierarchy>
        """.trimIndent()

        val result = DumpScreenTool(adb, serial).execute(NoArgs())

        assertEquals("(50, 25) [clickable] text=\"Settings\"", result)
    }

    @Test
    fun `tap runs input tap on the selected device`() = runTest {
        val result = TapTool(adb, serial).execute(TapArgs(x = 10, y = 20))

        assertEquals("Tapped.", result)
        assertEquals(listOf("adb", "-s", "emulator-5554", "shell", "input", "tap", "10", "20"), recorded.single())
    }

    @Test
    fun `swipe uses the default duration when none is given`() = runTest {
        SwipeTool(adb, serial).execute(SwipeArgs(x1 = 1, y1 = 2, x2 = 3, y2 = 4))

        assertEquals(
            listOf("adb", "-s", "emulator-5554", "shell", "input", "swipe", "1", "2", "3", "4", "300"),
            recorded.single(),
        )
    }
}
