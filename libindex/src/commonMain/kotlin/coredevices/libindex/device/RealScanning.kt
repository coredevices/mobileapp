package coredevices.libindex.device

import co.touchlab.kermit.Logger
import com.juul.kable.Scanner
import coredevices.libindex.Scanning
import io.rebble.libpebblecommon.connection.BleScanResult
import io.rebble.libpebblecommon.connection.bt.ble.transport.impl.asPebbleBleIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RealScanning(
    private val indexDeviceManager: IndexDeviceManager,
): Scanning {
    private var bleScanJob: Job? = null
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        private val BLE_SCANNING_TIMEOUT = 30.seconds
    }

    private fun scan(): Flow<BleScanResult> {
        return Scanner {
            filters {
                match {
                    services = listOf(Uuid.parse("607B5C9B-3700-4E94-F44A-2DF900BCB0C3"))
                }
            }
        }.advertisements
            .mapNotNull {
                val name = it.name ?: return@mapNotNull null
                val manufacturerData = it.manufacturerData ?: return@mapNotNull null
                BleScanResult(
                    identifier = it.identifier.asPebbleBleIdentifier(),
                    name = name,
                    rssi = it.rssi,
                    manufacturerData = manufacturerData
                )
            }
    }

    override fun startScan() {
        Logger.d("index startScan")
        bleScanJob?.cancel()
        indexDeviceManager.clearScanResults()
        val scanResults = scan()
        _isScanning.value = true
        bleScanJob = scope.launch {
            launch {
                delay(BLE_SCANNING_TIMEOUT)
                stopScan()
            }
            try {
                scanResults.collect {
                    indexDeviceManager.addScanResult(
                        IndexScanResult(
                            identifier = IndexIdentifier.fromPlatformAddress(it.identifier.asString),
                            name = it.name,
                            rssi = it.rssi,
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Ble scan failed" }
                stopScan()
            }
        }
    }

    override fun stopScan() {
        Logger.d("index stopBleScan")
        bleScanJob?.cancel()
        _isScanning.value = false
    }
}

