package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import co.touchlab.kermit.Logger
import com.juul.kable.Peripheral
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PAIRING_SERVICE_UUID
import io.rebble.libpebblecommon.connection.bt.ble.transport.asCbUuid
import io.rebble.libpebblecommon.connection.bt.ble.transport.asUuid
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBPeripheral
import kotlin.uuid.Uuid

actual fun peripheralFromIdentifier(identifier: PebbleBleIdentifier, name: String): Peripheral? {
    val peripheral = peripheralFromUuid(identifier.uuid)
    if (peripheral != null) {
        return peripheral
    }
    Logger.d("ios fallback: asking for connected devices..")
    val connected = CBCentralManager().retrieveConnectedPeripheralsWithServices(listOf(
        PAIRING_SERVICE_UUID.asCbUuid()
    )) as List<CBPeripheral>
    val match = connected.firstOrNull { it.name == name }
    if (match != null) {
        val fallbackPeripheral = peripheralFromUuid(match.identifier.asUuid())
        Logger.d("ios fallback: fallbackPeripheral = $fallbackPeripheral")
        if (fallbackPeripheral != null) {
            return fallbackPeripheral
        }
    }
    // For some reason, after restarting the app, even thought the UUID is initially invalid,
    // calling retrieveConnectedPeripheralsWithServices does not return the peripheral we want, but
    // it *does* make the next call to peripheralFromUuid work :confused:
    val peripheral2 = peripheralFromUuid(identifier.uuid)
    if (peripheral2 != null) {
        Logger.d("peripheral found after ios workaround!")
        return peripheral2
    }
    return null
}

private fun peripheralFromUuid(uuid: Uuid): Peripheral? = try {
    Peripheral(uuid) {
        logging {
//            level = Logging.Level.Data
        }
        // iOS will fail to connect without this
        forceCharacteristicEqualityByUuid = true
    }.also { Logger.d("peripheralFromUuid: created Peripheral!!") }
} catch (e: NoSuchElementException) {
    Logger.d("ios peripheral not found: $uuid")
    null
}

actual suspend fun Peripheral.requestMtuNative(mtu: Int): Int {
    throw IllegalStateException("not supported")
}

// iOS's CoreBluetooth cache is managed per-app-launch by the framework itself;
// there's no equivalent to Android's BluetoothGatt#refresh(). Returning false
// lets the caller skip its refresh-then-rediscover step and fall through to
// the normal discovery path.
actual suspend fun Peripheral.refreshServicesNative(): Boolean = false