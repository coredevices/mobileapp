package io.rebble.libpebblecommon.connection.bt

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleBtClassicIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.ConnectivityStatus
import kotlinx.coroutines.flow.Flow

actual fun isBonded(identifier: PebbleBleIdentifier): Boolean {
    TODO("Not yet implemented")
}

actual fun createBond(identifier: PebbleBleIdentifier): Boolean {
    TODO("Not yet implemented")
}

actual fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: PebbleBleIdentifier,
    connectivity: Flow<ConnectivityStatus>
): Flow<BluetoothDevicePairEvent> {
    TODO("Not yet implemented")
}

actual fun isBondedClassic(identifier: PebbleBtClassicIdentifier): Boolean {
    TODO("Not yet implemented")
}

actual fun createBondClassic(identifier: PebbleBtClassicIdentifier): Boolean {
    TODO("Not yet implemented")
}

actual fun getBluetoothClassicDevicePairEvents(
    context: AppContext,
    identifier: PebbleBtClassicIdentifier,
): Flow<BluetoothClassicDevicePairEvent> {
    TODO("Not yet implemented")
}