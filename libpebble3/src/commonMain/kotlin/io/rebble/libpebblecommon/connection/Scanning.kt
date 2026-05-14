package io.rebble.libpebblecommon.connection

import co.touchlab.kermit.Logger
import com.juul.kable.ManufacturerData
import com.oldguy.common.getShortAt
import io.rebble.libpebblecommon.ErrorTracker
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.bt.BluetoothState
import io.rebble.libpebblecommon.connection.bt.BluetoothStateProvider
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.connection.bt.ble.pebble.PebbleLeScanRecord
import io.rebble.libpebblecommon.connection.bt.ble.pebble.PebbleLeScanRecord.Companion.decodePebbleScanRecord
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import io.rebble.libpebblecommon.connection.bt.classic.transport.ClassicScanner
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.supportsBtClassic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class BleScanResult(
    val identifier: PebbleBleIdentifier,
    val name: String,
    val rssi: Int,
    val manufacturerData: ManufacturerData,
)

data class PebbleScanResult(
    val identifier: PebbleIdentifier,
    val name: String,
    val rssi: Int,
    val leScanRecord: PebbleLeScanRecord?,
)

class RealScanning(
    private val watchConnector: WatchConnector,
    private val bleScanner: BleScanner,
    private val classicScanner: ClassicScanner,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val bluetoothStateProvider: BluetoothStateProvider,
    private val errorTracker: ErrorTracker,
    private val watchConfig: WatchConfigFlow,
    private val blePlatformConfig: BlePlatformConfig,
) : Scanning {
    private var bleScanJob: Job? = null
    private var classicScanJob: Job? = null
    override val bluetoothEnabled: StateFlow<BluetoothState> = bluetoothStateProvider.state
    private val _isBleScanning = MutableStateFlow(false)
    override val isScanningBle: StateFlow<Boolean> = _isBleScanning.asStateFlow()
    private val _isClassicScanning = MutableStateFlow(false)
    override val isScanningClassic: StateFlow<Boolean> = _isClassicScanning.asStateFlow()

    override fun startBleScan() {
        Logger.d("startBleScan")
        bleScanJob?.cancel()
        watchConnector.clearScanResults()
        val scanResults = bleScanner.scan()
        _isBleScanning.value = true
        bleScanJob = libPebbleCoroutineScope.launch {
            launch {
                delay(BLE_SCANNING_TIMEOUT)
                stopBleScan()
            }
            try {
                scanResults.collect {
                    if (it.manufacturerData.code !in VENDOR_IDS) {
                        return@collect
                    }
                    val pebbleScanRecord = it.manufacturerData.data.decodePebbleScanRecord()
                    if (shouldHideLegacyClassicWatch(pebbleScanRecord)) {
                        return@collect
                    }
                    val device = PebbleScanResult(
                        identifier = it.identifier,
                        name = it.name,
                        rssi = it.rssi,
                        leScanRecord = pebbleScanRecord,
                    )
                    watchConnector.addScanResult(device)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Ble scan failed" }
                errorTracker.reportError(UserFacingError.FailedToScan("Failed to scan for watches"))
                stopBleScan()
            }
        }
    }

    override fun stopBleScan() {
        Logger.d("stopBleScan")
        bleScanJob?.cancel()
        _isBleScanning.value = false
    }

    override fun startClassicScan() {
        Logger.d("startClassicScan")
        classicScanJob?.cancel()
        watchConnector.clearScanResults()
        _isClassicScanning.value = true
        classicScanJob = libPebbleCoroutineScope.launch {
            launch {
                delay(CLASSIC_SCANNING_TIMEOUT)
                stopClassicScan()
            }
            try {
                classicScanner.scan().collect {
                    watchConnector.addScanResult(it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Classic scan failed" }
                errorTracker.reportError(UserFacingError.FailedToScan("Failed to scan for classic watches"))
                stopClassicScan()
            }
        }
    }

    override fun stopClassicScan() {
        Logger.d("stopClassicScan")
        classicScanJob?.cancel()
        _isClassicScanning.value = false
    }

    /**
     * Hide Aplite/Basalt/Chalk watches from BLE scan results on platforms that support BT Classic
     * (Android), so users go through the dedicated Classic scan instead. Older firmware without
     * extendedInfo can't be classified — we let those through pessimistically.
     */
    private fun shouldHideLegacyClassicWatch(record: PebbleLeScanRecord): Boolean {
        if (!blePlatformConfig.supportsBtClassic) return false
        if (watchConfig.value.allowLegacyWatchesInBleScan) return false
        val hardwarePlatform = record.extendedInfo?.hardwarePlatform ?: return false
        val watchType = WatchHardwarePlatform.fromProtocolNumber(hardwarePlatform.toUByte()).watchType
        return watchType.supportsBtClassic()
    }

    companion object {
        val PEBBLE_VENDOR_ID = byteArrayOf(0x54, 0x01).getShortAt(0).toInt()
        val CORE_VENDOR_ID = byteArrayOf(0xEA.toByte(), 0x0E).getShortAt(0).toInt()
        val VENDOR_IDS = listOf(PEBBLE_VENDOR_ID, CORE_VENDOR_ID)
        private val BLE_SCANNING_TIMEOUT = 30.seconds
        private val CLASSIC_SCANNING_TIMEOUT = 30.seconds
    }
}
