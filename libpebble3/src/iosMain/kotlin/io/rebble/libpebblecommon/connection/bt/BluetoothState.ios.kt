package io.rebble.libpebblecommon.connection.bt

import co.touchlab.kermit.Logger
import com.juul.kable.Scanner
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PAIRING_SERVICE_UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.darwin.NSObject

private class CentralStateObserver : NSObject(), CBCentralManagerDelegateProtocol {
    private val logger = Logger.withTag("CentralStateObserver")
    private val _state = MutableStateFlow(BluetoothState.Disabled)
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    // Kable's own CentralManager shows the power alert; don't show a second one.
    private val manager = CBCentralManager(this, null, mapOf<Any?, Any>(CBCentralManagerOptionShowPowerAlertKey to false))

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        logger.d { "centralManagerDidUpdateState: ${central.state}" }
        _state.value = if (central.state == CBManagerStatePoweredOn) BluetoothState.Enabled else BluetoothState.Disabled
    }
}

// Top-level so the manager (which only weakly holds its delegate) lives for the whole process.
private val centralStateObserver by lazy { CentralStateObserver() }

actual fun nativeBluetoothStateFlow(appContext: AppContext): Flow<BluetoothState> {
    // Building a Scanner creates Kable's shared CentralManager now. Peripheral lookups return nothing
    // until it has powered on, and WatchManager forgets a known watch whose lookup fails.
    Scanner { filters { match { services = listOf(PAIRING_SERVICE_UUID) } } }
    return centralStateObserver.state
}

actual fun locationServicesBlockScanning(appContext: AppContext): Boolean = false
