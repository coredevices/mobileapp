package io.rebble.libpebblecommon.connection.bt

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleBtClassicIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.ConnectivityWatcher
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.flow.Flow

internal expect fun isBonded(identifier: PebbleBleIdentifier): Boolean

internal expect fun createBond(identifier: PebbleBleIdentifier): Boolean

internal class BluetoothDevicePairEvent(val device: PebbleBleIdentifier, val bondState: Int, val unbondReason: Int?)

internal expect fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: PebbleBleIdentifier,
    connectivityWatcher: ConnectivityWatcher,
    connectionScope: ConnectionCoroutineScope,
): Flow<BluetoothDevicePairEvent>

internal expect fun isBondedClassic(identifier: PebbleBtClassicIdentifier): Boolean

internal expect fun createBondClassic(identifier: PebbleBtClassicIdentifier): Boolean

internal class BluetoothClassicDevicePairEvent(val device: PebbleBtClassicIdentifier, val bondState: Int, val unbondReason: Int?)

internal expect fun getBluetoothClassicDevicePairEvents(
    context: AppContext,
    identifier: PebbleBtClassicIdentifier,
): Flow<BluetoothClassicDevicePairEvent>
