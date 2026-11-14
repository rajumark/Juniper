package raju.shingadiya.juniper.adb

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdbClientTest {

    private val recorded = mutableListOf<List<String>>()

    private fun clientWith(
        exitCode: Int = 0,
        stdout: (List<String>) -> String = { "" },
        stderr: String = "",
    ): AdbClient {
        val runner = CommandRunner { command, _ ->
            recorded += command
            CommandResult(exitCode, stdout(command), stderr)
        }
        return AdbClient(adbPath = "adb", runner = runner)
    }

    @Test
    fun `devices parses adb devices -l output`() = runTest {
        val output = """
            List of devices attached
            emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1
            R58M123ABC             unauthorized usb:1-1 transport_id:2

        """.trimIndent()
        val client = clientWith(stdout = { output })

        val devices = client.devices()

        assertEquals(listOf("adb", "devices", "-l"), recorded.single())
        assertEquals(2, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
        assertTrue(devices[0].isReady)
        assertEquals("sdk_gphone64_arm64", devices[0].properties["model"])
        assertEquals("R58M123ABC", devices[1].serial)
        assertFalse(devices[1].isReady)
        assertEquals("unauthorized", devices[1].state)
    }

    @Test
    fun `devices skips the daemon startup banner`() {
        val parsed = AdbClient.parseDevices(
            "* daemon not running; starting now at tcp:5037\n* daemon started successfully\nList of devices attached\n"
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `tap runs input tap for the given serial`() = runTest {
        val client = clientWith()

        client.tap("emulator-5554", 540, 1200)

        assertEquals(
            listOf("adb", "-s", "emulator-5554", "shell", "input", "tap", "540", "1200"),
            recorded.single(),
        )
    }

    @Test
    fun `swipe passes all coordinates and duration`() = runTest {
        val client = clientWith()

        client.swipe("dev", 100, 1500, 100, 500, durationMs = 400)

        assertEquals(
            listOf("adb", "-s", "dev", "shell", "input", "swipe", "100", "1500", "100", "500", "400"),
            recorded.single(),
        )
    }

    @Test
    fun `inputText sends escaped text as one argument`() = runTest {
        val client = clientWith()

        client.inputText("dev", "hello world & 50%")

        assertEquals(
            listOf("adb", "-s", "dev", "shell", "input", "text", "hello%sworld%s\\&%s50\\%"),
            recorded.single(),
        )
    }

    @Test
    fun `escapeInputText escapes spaces, percent and shell characters`() {
        assertEquals("hello%sworld", AdbClient.escapeInputText("hello world"))
        assertEquals("a\\&b\\;c", AdbClient.escapeInputText("a&b;c"))
        assertEquals("50\\%", AdbClient.escapeInputText("50%"))
        assertEquals("user@example.com", AdbClient.escapeInputText("user@example.com"))
    }

    @Test
    fun `keyEvent and startActivity build the right commands`() = runTest {
        val client = clientWith()

        client.keyEvent("dev", "KEYCODE_BACK")
        client.startActivity("dev", "com.android.settings/.Settings")

        assertEquals(listOf("adb", "-s", "dev", "shell", "input", "keyevent", "KEYCODE_BACK"), recorded[0])
        assertEquals(
            listOf("adb", "-s", "dev", "shell", "am", "start", "-n", "com.android.settings/.Settings"),
            recorded[1],
        )
    }

    @Test
    fun `non-zero exit throws AdbException with stderr detail`() = runTest {
        val client = clientWith(exitCode = 1, stderr = "error: device offline")

        val error = assertFailsWith<AdbException> { client.tap("dev", 1, 2) }

        assertTrue(error.message!!.contains("exit 1"))
        assertTrue(error.message!!.contains("device offline"))
    }

    @Test
    fun `blank serial is rejected before running adb`() = runTest {
        val client = clientWith()

        assertFailsWith<IllegalArgumentException> { client.tap(" ", 1, 2) }
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `dumpUiHierarchy dumps to a file then reads it back`() = runTest {
        val xml = """
            UI hierchary dumped to: /sdcard/juniper_window_dump.xml
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" clickable="false" enabled="true" bounds="[0,0][1080,2400]">
                <node index="0" text="Log in" resource-id="com.app:id/login" class="android.widget.Button" content-desc="" clickable="true" enabled="true" bounds="[100,1000][980,1100]" />
                <node index="1" text="" resource-id="" class="android.widget.EditText" content-desc="Email" clickable="true" enabled="false" bounds="[100,500][980,600]" />
              </node>
            </hierarchy>
        """.trimIndent()
        val client = clientWith(stdout = { command -> if (command.contains("cat")) xml else "" })

        val nodes = client.dumpUiHierarchy("dev")

        assertEquals(listOf("uiautomator", "dump"), recorded[0].drop(4).take(2))
        assertEquals(listOf("cat"), recorded[1].drop(4).take(1))
        assertEquals(3, nodes.size)
        val button = nodes.first { it.text == "Log in" }
        assertTrue(button.clickable)
        assertEquals("com.app:id/login", button.resourceId)
        assertEquals(540, button.bounds.centerX)
        assertEquals(1050, button.bounds.centerY)
        val field = nodes.first { it.contentDescription == "Email" }
        assertFalse(field.enabled)
    }

    @Test
    fun `parseUiHierarchy rejects output without xml`() {
        assertFailsWith<AdbException> { AdbClient.parseUiHierarchy("no dump here") }
    }

    @Test
    fun `parseUiHierarchy refuses DOCTYPE declarations`() {
        val malicious = """<?xml version="1.0"?><!DOCTYPE hierarchy [<!ENTITY x SYSTEM "file:///etc/passwd">]><hierarchy>&x;</hierarchy>"""
        assertFailsWith<AdbException> { AdbClient.parseUiHierarchy(malicious) }
    }

    @Test
    fun `parseBounds handles valid and invalid strings`() {
        assertEquals(Bounds(0, 96, 1080, 2400), AdbClient.parseBounds("[0,96][1080,2400]"))
        assertNull(AdbClient.parseBounds("not bounds"))
    }
}
