package io.rebble.libpebblecommon.connection.bt.ble.pebble

import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PAIRING_SERVICE_UUID
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PPOG_RESET_CHARACTERISTIC
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattWriteType

class PPoGReset(private val blePlatformConfig: BlePlatformConfig) {
    /**
     * Trigger the PPoG reset characteristic if it exists and is supported
     *
     * @return whether it was triggered.
     */
    suspend fun triggerPpogResetIfNeeded(gattClient: ConnectedGattClient): Boolean {
        if (!blePlatformConfig.sendPpogResetOnDisconnection) {
            return false
        }
        return gattClient.writeCharacteristic(
            serviceUuid = PAIRING_SERVICE_UUID,
            characteristicUuid = PPOG_RESET_CHARACTERISTIC,
            value = byteArrayOf(1),
            writeType = GattWriteType.WithResponse,
        )
    }
}