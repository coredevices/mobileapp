package io.rebble.libpebblecommon.connection.bt.ble

import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.DEFAULT_MTU
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.MAX_RX_WINDOW
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.MAX_TX_WINDOW

data class BlePlatformConfig(
    val pinAddress: Boolean = true,
    val phoneRequestsPairing: Boolean = true,
    val writeConnectivityTrigger: Boolean = true,
    val initialMtu: Int = DEFAULT_MTU,
    val desiredTxWindow: Int = MAX_TX_WINDOW,
    val desiredRxWindow: Int = MAX_RX_WINDOW,
    val useNativeMtu: Boolean = true,
    val sendPpogResetOnDisconnection: Boolean = false,
    val delayBleConnectionsAfterAppStart: Boolean = false,
    val delayBleDisconnections: Boolean = true,
    val fallbackToResetRequest: Boolean = false,
    val closeGattServerWhenBtDisabled: Boolean = true,
    val supportsBtClassic: Boolean = false,
    val supportsPpogResetCharacteristic: Boolean = false,
)