package raju.shingadiya.juniper.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import raju.shingadiya.juniper.adb.AdbClient
import raju.shingadiya.juniper.adb.AdbDevice
import raju.shingadiya.juniper.adb.AdbException
import raju.shingadiya.juniper.adb.AdbNotFoundException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What adb reports for a device. Only [READY] devices can receive commands. */
enum class DeviceStatus { READY, UNAUTHORIZED, OFFLINE, OTHER }

data class DeviceInfo(
    val serial: String,
    val status: DeviceStatus,
    val rawState: String,
    val model: String?,
) {
    val isReady: Boolean get() = status == DeviceStatus.READY
    val isEmulator: Boolean get() = serial.startsWith("emulator-")

    /** Human name: the model with underscores replaced by spaces, or the serial if the model is unknown. */
    val displayName: String get() = model?.replace('_', ' ')?.takeIf { it.isNotBlank() } ?: serial

    /** Short explanation of the status, shown under the device in the menu. */
    val statusText: String
        get() = when (status) {
            DeviceStatus.READY -> "ready"
            DeviceStatus.UNAUTHORIZED -> "unauthorized: accept the USB debugging prompt on the device"
            DeviceStatus.OFFLINE -> "offline: reconnect the cable or restart the emulator"
            DeviceStatus.OTHER -> "not ready (adb state: $rawState)"
        }
}

/**
 * Everything the device menu needs.
 *
 * - [selectedSerial] is the device commands go to. It is null when no ready device exists.
 * - [error] is set when adb failed; [adbMissing] is set when adb itself could not be started.
 * - [hasLoaded] is false until the first check finishes, so the UI can show "Checking…".
 */
data class DeviceSnapshot(
    val devices: List<DeviceInfo> = emptyList(),
    val selectedSerial: String? = null,
    val error: String? = null,
    val adbMissing: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
) {
    val selected: DeviceInfo? get() = devices.find { it.serial == selectedSerial }
}

/**
 * Tracks the devices adb can see and which one is selected.
 *
 * Rules:
 * - When nothing is selected, or the selected device disappears, the first ready device is selected.
 * - A selected device that goes offline or unauthorized stays selected, so it comes back on its own.
 * - Devices that are not ready cannot be selected.
 * - If adb fails, the list is cleared but the selected serial is remembered, so the selection
 *   comes back when the device reappears.
 */
class DeviceManager(
    private val adb: AdbClient,
    private val pollInterval: Duration = 3.seconds,
) {
    private val _state = MutableStateFlow(DeviceSnapshot())
    val state: StateFlow<DeviceSnapshot> = _state.asStateFlow()

    /** Checks the device list every [pollInterval] until the returned job is cancelled. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (true) {
            refresh()
            delay(pollInterval)
        }
    }

    /** Runs one check of the device list now. Never throws; problems are reported in [state]. */
    suspend fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        try {
            val infos = adb.devices().map { toInfo(it) }
            _state.update { reconcile(it, infos) }
        } catch (e: AdbNotFoundException) {
            _state.update { it.copy(devices = emptyList(), error = e.message, adbMissing = true) }
        } catch (e: AdbException) {
            _state.update { it.copy(devices = emptyList(), error = e.message, adbMissing = false) }
        } finally {
            _state.update { it.copy(isRefreshing = false, hasLoaded = true) }
        }
    }

    /** Selects a ready device. Returns false if the serial is unknown or the device is not ready. */
    fun select(serial: String): Boolean {
        val device = _state.value.devices.find { it.serial == serial } ?: return false
        if (!device.isReady) return false
        _state.update { it.copy(selectedSerial = serial) }
        return true
    }

    private fun reconcile(current: DeviceSnapshot, infos: List<DeviceInfo>): DeviceSnapshot {
        val keep = current.selectedSerial?.takeIf { serial -> infos.any { it.serial == serial } }
        val selected = keep ?: infos.firstOrNull { it.isReady }?.serial
        return current.copy(
            devices = infos,
            selectedSerial = selected,
            error = null,
            adbMissing = false,
        )
    }

    companion object {
        internal fun toInfo(device: AdbDevice): DeviceInfo = DeviceInfo(
            serial = device.serial,
            status = when (device.state) {
                "device" -> DeviceStatus.READY
                "unauthorized" -> DeviceStatus.UNAUTHORIZED
                "offline" -> DeviceStatus.OFFLINE
                else -> DeviceStatus.OTHER
            },
            rawState = device.state,
            model = device.properties["model"],
        )
    }
}
