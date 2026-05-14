package io.rebble.libpebblecommon.connection.bt

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleBtClassicIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.ConnectivityStatus
import kotlinx.coroutines.flow.Flow

expect fun isBonded(identifier: PebbleBleIdentifier): Boolean

expect fun createBond(identifier: PebbleBleIdentifier): Boolean

class BluetoothDevicePairEvent(val device: PebbleBleIdentifier, val bondState: Int, val unbondReason: Int?)

expect fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: PebbleBleIdentifier,
    connectivity: Flow<ConnectivityStatus>,
): Flow<BluetoothDevicePairEvent>

expect fun isBondedClassic(identifier: PebbleBtClassicIdentifier): Boolean

expect fun createBondClassic(identifier: PebbleBtClassicIdentifier): Boolean

class BluetoothClassicDevicePairEvent(val device: PebbleBtClassicIdentifier, val bondState: Int, val unbondReason: Int?)

expect fun getBluetoothClassicDevicePairEvents(
    context: AppContext,
    identifier: PebbleBtClassicIdentifier,
): Flow<BluetoothClassicDevicePairEvent>
