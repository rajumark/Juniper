package raju.shingadiya.juniper.device

import kotlinx.coroutines.test.runTest
import raju.shingadiya.juniper.adb.AdbClient
import raju.shingadiya.juniper.adb.AdbException
import raju.shingadiya.juniper.adb.AdbNotFoundException
import raju.shingadiya.juniper.adb.CommandResult
import raju.shingadiya.juniper.adb.CommandRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceManagerTest {

    /** Fake adb: the test sets what `adb devices` prints, or makes it fail. */
    private class FakeAdb {
        var output: String = header
        var failure: Exception? = null
    }

    private fun managerFor(fake: FakeAdb): DeviceManager {
        val runner = CommandRunner { _, _ ->
            fake.failure?.let { throw it }
            CommandResult(exitCode = 0, stdout = fake.output, stderr = "")
        }
        return DeviceManager(AdbClient(adbPath = "adb", runner = runner))
    }

    private val ready1 = "emulator-5554          device product:sdk model:Pixel_7 device:x transport_id:1"
    private val ready2 = "R58M123ABC             device product:p model:SM_G991B device:y transport_id:2"
    private val unauthorized = "ZY22ABC               unauthorized usb:1-1 transport_id:3"

    private fun listing(vararg lines: String): String = (listOf(header) + lines).joinToString("\n") + "\n"

    @Test
    fun `no devices leaves nothing selected and no error`() = runTest {
        val fake = FakeAdb()
        val manager = managerFor(fake)

        manager.refresh()

        val state = manager.state.value
        assertTrue(state.devices.isEmpty())
        assertNull(state.selectedSerial)
        assertNull(state.error)
        assertTrue(state.hasLoaded)
    }

    @Test
    fun `a single ready device is selected by default`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1) }
        val manager = managerFor(fake)

        manager.refresh()

        val state = manager.state.value
        assertEquals("emulator-5554", state.selectedSerial)
        assertEquals("Pixel 7", state.selected?.displayName)
        assertTrue(state.selected!!.isEmulator)
    }

    @Test
    fun `unauthorized device is listed but not selected`() = runTest {
        val fake = FakeAdb().apply { output = listing(unauthorized) }
        val manager = managerFor(fake)

        manager.refresh()

        val state = manager.state.value
        assertEquals(1, state.devices.size)
        assertEquals(DeviceStatus.UNAUTHORIZED, state.devices[0].status)
        assertNull(state.selectedSerial)
        assertFalse(manager.select(state.devices[0].serial))
    }

    @Test
    fun `first ready device is picked when several are connected`() = runTest {
        val fake = FakeAdb().apply { output = listing(unauthorized, ready2, ready1) }
        val manager = managerFor(fake)

        manager.refresh()

        assertEquals("R58M123ABC", manager.state.value.selectedSerial)
    }

    @Test
    fun `user can switch to another ready device and it stays selected on refresh`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1, ready2) }
        val manager = managerFor(fake)
        manager.refresh()

        assertTrue(manager.select("R58M123ABC"))
        manager.refresh()

        assertEquals("R58M123ABC", manager.state.value.selectedSerial)
    }

    @Test
    fun `selecting an unknown or not ready device is rejected`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1, unauthorized) }
        val manager = managerFor(fake)
        manager.refresh()

        assertFalse(manager.select("does-not-exist"))
        assertFalse(manager.select("ZY22ABC"))
        assertEquals("emulator-5554", manager.state.value.selectedSerial)
    }

    @Test
    fun `selection moves to another ready device when the selected one is unplugged`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1, ready2) }
        val manager = managerFor(fake)
        manager.refresh()
        manager.select("R58M123ABC")

        fake.output = listing(ready1)
        manager.refresh()

        assertEquals("emulator-5554", manager.state.value.selectedSerial)
    }

    @Test
    fun `nothing is selected when the last ready device is unplugged`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1) }
        val manager = managerFor(fake)
        manager.refresh()

        fake.output = listing()
        manager.refresh()

        assertNull(manager.state.value.selectedSerial)
        assertTrue(manager.state.value.devices.isEmpty())
    }

    @Test
    fun `selected device that goes offline stays selected and recovers`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1) }
        val manager = managerFor(fake)
        manager.refresh()

        fake.output = listing("emulator-5554          offline transport_id:1")
        manager.refresh()
        assertEquals("emulator-5554", manager.state.value.selectedSerial)
        assertEquals(DeviceStatus.OFFLINE, manager.state.value.selected?.status)

        fake.output = listing(ready1)
        manager.refresh()
        assertEquals(DeviceStatus.READY, manager.state.value.selected?.status)
    }

    @Test
    fun `missing adb is reported as adbMissing with guidance`() = runTest {
        val fake = FakeAdb().apply { failure = AdbNotFoundException("Could not run 'adb'") }
        val manager = managerFor(fake)

        manager.refresh()

        val state = manager.state.value
        assertTrue(state.adbMissing)
        assertTrue(state.error!!.contains("Could not run"))
        assertTrue(state.devices.isEmpty())
    }

    @Test
    fun `adb command failure is reported and selection returns when adb recovers`() = runTest {
        val fake = FakeAdb().apply { output = listing(ready1) }
        val manager = managerFor(fake)
        manager.refresh()

        fake.failure = AdbException("adb devices failed (exit 1): server is down")
        manager.refresh()
        val failed = manager.state.value
        assertTrue(failed.error!!.contains("server is down"))
        assertFalse(failed.adbMissing)
        assertTrue(failed.devices.isEmpty())

        fake.failure = null
        manager.refresh()
        assertEquals("emulator-5554", manager.state.value.selectedSerial)
        assertNull(manager.state.value.error)
    }

    @Test
    fun `device without a model name shows its serial`() {
        val info = DeviceManager.toInfo(
            raju.shingadiya.juniper.adb.AdbDevice("ABC123", "device", emptyMap())
        )
        assertEquals("ABC123", info.displayName)
        assertTrue(info.isReady)
    }

    private companion object {
        const val header = "List of devices attached"
    }
}
