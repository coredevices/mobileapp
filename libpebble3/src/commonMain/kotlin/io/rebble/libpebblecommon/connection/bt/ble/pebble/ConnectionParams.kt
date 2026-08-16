package io.rebble.libpebblecommon.connection.bt.ble.pebble

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.CONNECTION_PARAMETERS_CHARACTERISTIC
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PAIRING_SERVICE_UUID
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattWriteType
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.launch

class ConnectionParams(private val scope: ConnectionCoroutineScope) {
    suspend fun subscribeAndConfigure(gattClient: ConnectedGattClient): Boolean {
        // TODO scope this
        val sub = gattClient.subscribeToCharacteristic(PAIRING_SERVICE_UUID, CONNECTION_PARAMETERS_CHARACTERISTIC)
        if (sub == null) {
            Logger.i("error subscribing to connection params (not present on core watches yet)")
            return false
        }
        scope.launch {
            sub.collect {
                Logger.d("connection params changed: ${it.joinToString()}")
            }
        }
        val value = byteArrayOf(0, 1)
        return gattClient.writeCharacteristic(PAIRING_SERVICE_UUID, CONNECTION_PARAMETERS_CHARACTERISTIC, value, GattWriteType.WithResponse)
    }
}