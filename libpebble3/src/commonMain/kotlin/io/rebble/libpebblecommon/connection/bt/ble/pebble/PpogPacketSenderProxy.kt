package io.rebble.libpebblecommon.connection.bt.ble.pebble

import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGPacketSender
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient

/**
 * Delegates [PPoGPacketSender] to either [PpogClient] (watch hosts the
 * reversed PPoG service, phone is GATT client) or [PpogServer] (phone hosts
 * the forward PPoG service, watch is GATT client). The choice is made
 * post-discovery by [PebbleBle] based on whether the watch advertises the
 * reversed PPoG service.
 */
class PpogPacketSenderProxy(
    private val ppogClient: PpogClient,
    private val ppogServer: PpogServer,
) : PPoGPacketSender {

    private enum class Mode { Forward, Reversed }

    private var mode: Mode? = null

    val isReversed: Boolean
        get() = mode == Mode.Reversed

    suspend fun configureReversed(device: ConnectedGattClient, config: PpogClientConfig) {
        ppogClient.init(device, config)
        mode = Mode.Reversed
    }

    fun configureForward() {
        mode = Mode.Forward
    }

    override suspend fun sendPacket(packet: ByteArray): Boolean = when (mode) {
        Mode.Reversed -> ppogClient.sendPacket(packet)
        Mode.Forward -> ppogServer.sendPacket(packet)
        null -> error("PpogPacketSenderProxy not configured")
    }

    override fun wasRestoredWithSubscribedCentral(): Boolean = when (mode) {
        Mode.Forward -> ppogServer.wasRestoredWithSubscribedCentral()
        else -> false
    }
}
