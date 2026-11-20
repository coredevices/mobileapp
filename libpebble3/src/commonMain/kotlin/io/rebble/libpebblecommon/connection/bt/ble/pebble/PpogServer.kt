package io.rebble.libpebblecommon.connection.bt.ble.pebble

import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PPOGATT_DEVICE_CHARACTERISTIC_SERVER
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PPOGATT_DEVICE_SERVICE_UUID_SERVER
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGPacketSender
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattServerManager

internal class PpogServer(
    private val identifier: PebbleBleIdentifier,
    private val gattServerManager: GattServerManager,
) : PPoGPacketSender {
    override suspend fun sendPacket(packet: ByteArray): Boolean {
        return gattServerManager.sendData(
            identifier = identifier,
            serviceUuid = PPOGATT_DEVICE_SERVICE_UUID_SERVER,
            characteristicUuid = PPOGATT_DEVICE_CHARACTERISTIC_SERVER,
            data = packet
        )
    }

    override fun wasRestoredWithSubscribedCentral(): Boolean {
        return gattServerManager.wasRestoredWithSubscribedCentral()
    }
}