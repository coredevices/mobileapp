package io.rebble.libpebblecommon.connection.bt.ble.pebble

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGPacketSender
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGStream
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattWriteType
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

enum class ReversePpogVersion {
    V1,
    V2,
}

data class PpogClientConfig(
    val serviceUuid: Uuid,
    val notifyCharacteristic: Uuid,
    val writeCharacteristic: Uuid,
    val version: ReversePpogVersion,
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
            try {
                flow.collect {
                    pPoGStream.inboundPPoGBytesChannel.send(it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Kable throws in the flow when the CCCD write fails — this
                // happens on iOS when CoreBluetooth's GATT cache still lists a
                // reversed-PPoG service that no longer exists on the watch
                // ("The handle is invalid"). Propagate to init()'s awaiter so
                // PebbleBle can fall back to forward. If the deferred was
                // already completed (subscription succeeded and this is a
                // later runtime failure), rethrow so the connection scope's
                // exceptionHandler picks it up normally.
                if (!cccdWritten.completeExceptionally(e)) {
                    throw e
                } else {
                    Logger.w("PpogClient reversed subscribe failed", e)
                }
            }
        }
        // Belt-and-braces: if Kable somehow routes the setNotify error via a
        // path that doesn't hit our flow.collect (has happened on iOS —
        // Observers wraps exceptions through an ObservationExceptionHandler),
        // fall through after a short timeout rather than blocking forever.
        val subscribed = withTimeoutOrNull(SUBSCRIBE_TIMEOUT) {
            cccdWritten.await()
            true
        } == true
        if (!subscribed) {
            throw IllegalStateException("reversed PPoG CCCD subscribe timed out")
        }
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

    companion object {
        private val SUBSCRIBE_TIMEOUT = 5.seconds
    }
}
