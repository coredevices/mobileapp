package io.rebble.libpebblecommon.connection

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.PacketPriority
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

internal interface PebbleProtocolHandler {
    val inboundMessages: SharedFlow<PebblePacket>
    val rawInboundMessages: Flow<ByteArray>
    suspend fun send(message: PebblePacket, priority: PacketPriority = PacketPriority.NORMAL)
    suspend fun send(message: ByteArray, priority: PacketPriority = PacketPriority.NORMAL)
}

internal class RealPebbleProtocolHandler(
    private val pebbleProtocolStreams: PebbleProtocolStreams,
    connectionScope: ConnectionCoroutineScope,
    identifier: PebbleIdentifier,
) : PebbleProtocolHandler {
    private val logger = Logger.withTag("PebbleProtocol-${identifier}")
    override val inboundMessages: SharedFlow<PebblePacket> =
        pebbleProtocolStreams.inboundMessagesFlow
            .map { it.packet }.shareIn(connectionScope, SharingStarted.Lazily)
    override val rawInboundMessages: Flow<ByteArray> =
        pebbleProtocolStreams.inboundMessagesFlow.map { it.rawBytes.asByteArray() }

    override suspend fun send(message: PebblePacket, priority: PacketPriority) {
        logger.d("sending $message")
        pebbleProtocolStreams.outboundPPBytes.send(message.serialize().asByteArray())
    }

    override suspend fun send(message: ByteArray, priority: PacketPriority) {
        logger.d("sending ${message.joinToString()}")
        pebbleProtocolStreams.outboundPPBytes.send(message)
    }
}
