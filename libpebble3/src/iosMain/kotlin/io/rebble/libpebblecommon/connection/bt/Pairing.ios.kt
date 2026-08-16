package io.rebble.libpebblecommon.connection.bt

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleBtClassicIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.ConnectivityWatcher
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_BONDED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_NONE
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

actual fun isBonded(identifier: PebbleBleIdentifier): Boolean {
    return true
}

actual fun createBond(identifier: PebbleBleIdentifier): Boolean {
    return true
}

private val logger = Logger.withTag("Pairing.ios")

actual fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: PebbleBleIdentifier,
    connectivityWatcher: ConnectivityWatcher,
    connectionScope: ConnectionCoroutineScope,
): Flow<BluetoothDevicePairEvent> = connectivityWatcher.status
    .map {
        if (it.encrypted && !it.paired) {
            connectionScope.launch {
                logger.w { "encrypted but not paired; waiting 2 seconds then refreshing manually" }
                delay(2.seconds)
                connectivityWatcher.readValue()
            }
        }
        BluetoothDevicePairEvent(
            device = identifier,
            bondState = when {
                it.paired && it.encrypted -> BOND_BONDED
                else -> BOND_NONE
            },
            unbondReason = -1,
        )
    }

actual fun isBondedClassic(identifier: PebbleBtClassicIdentifier): Boolean =
    throw UnsupportedOperationException("BT Classic not supported on iOS")

actual fun createBondClassic(identifier: PebbleBtClassicIdentifier): Boolean =
    throw UnsupportedOperationException("BT Classic not supported on iOS")

actual fun getBluetoothClassicDevicePairEvents(
    context: AppContext,
    identifier: PebbleBtClassicIdentifier,
): Flow<BluetoothClassicDevicePairEvent> =
    throw UnsupportedOperationException("BT Classic not supported on iOS")