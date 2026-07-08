package io.rebble.libpebblecommon.connection.bt.ble.pebble

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGPacketSender
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGStream
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattWriteType
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

data class PpogClientConfig(
    val serviceUuid: Uuid,
    val notifyCharacteristic: Uuid,
    val writeCharacteristic: Uuid,
)

class PpogClient(
    private val pPoGStream: PPoGStream,
    private val scope: ConnectionCoroutineScope,
) : PPoGPacketSender {
    private lateinit var gattClient: ConnectedGattClient
    private lateinit var config: PpogClientConfig

    suspend fun init(client: ConnectedGattClient, config: PpogClientConfig) {
        Logger.d("PpogClient init() service=${config.serviceUuid}")
        gattClient = client
        this.config = config
        // The CCCD write is done lazily by Kable on first collection. Wait for
        // it to actually land on the peer before returning — otherwise the
        // caller's first sendPacket() can race the CCCD write into the ATT
        // queue, arriving at the watch before the subscribe event and getting
        // dropped by ppogatt_reversed_handle_data (no client for conn yet).
        val cccdWritten = CompletableDeferred<Unit>()
        val flow = gattClient.subscribeToCharacteristic(
            serviceUuid = config.serviceUuid,
            characteristicUuid = config.notifyCharacteristic,
            onSubscription = { cccdWritten.complete(Unit) },
        )
        if (flow == null) {
            Logger.e("error subscribing to reverse data characteristic")
            return
        }
        scope.launch {
            flow.collect {
                pPoGStream.inboundPPoGBytesChannel.send(it)
            }
        }
        cccdWritten.await()
        Logger.d("PpogClient subscribed")
    }

    override suspend fun sendPacket(packet: ByteArray): Boolean {
        return gattClient.writeCharacteristic(
            serviceUuid = config.serviceUuid,
            characteristicUuid = config.writeCharacteristic,
            value = packet,
            writeType = GattWriteType.NoResponse,
        )
    }

    override fun wasRestoredWithSubscribedCentral(): Boolean = false
}
