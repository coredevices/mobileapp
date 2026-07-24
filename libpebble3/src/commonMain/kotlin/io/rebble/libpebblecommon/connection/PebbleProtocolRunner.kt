package io.rebble.libpebblecommon.connection

import co.touchlab.kermit.Logger
import com.oldguy.common.getUShortAt
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.peek
import io.ktor.utils.io.readByteArray
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlin.time.Duration.Companion.seconds

data class InboundPPMessage(
    val packet: PebblePacket,
    val rawBytes: UByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as InboundPPMessage

        if (packet != other.packet) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packet.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

class PebbleProtocolStreams(
    val inboundPPBytes: ByteChannel = ByteChannel(),
    // Using ByteArray here because I'm not 100% sure how the watch handles multiple PP messages
    // within a single PPoG packet (we could make this [Byte] (or use source/sink) if that
    // works OK (for all knowns LE watches)).
    val outboundPPBytes: Channel<ByteArray> = Channel(capacity = 100),
    val inboundMessagesFlow: MutableSharedFlow<InboundPPMessage> = MutableSharedFlow(
        extraBufferCapacity = 100,
    ),
)

class PebbleProtocolRunner(
    private val pebbleProtocolStreams: PebbleProtocolStreams,
    private val identifier: PebbleIdentifier,
) {
    private val logger = Logger.withTag("PebbleProtocolRunner-$identifier")

    suspend fun run() {
        try {
            while (true) {
                val sizeBytes = pebbleProtocolStreams.inboundPPBytes.peek(2)
                val sizeArray = sizeBytes?.toByteArray()?.asUByteArray()
                    ?: throw IOException("couldn't read size")
                val payloadSize = sizeArray.getUShortAt(0, littleEndian = false)
                val packetSize = payloadSize + PP_HEADER_SIZE
                val packetBytes =
                    pebbleProtocolStreams.inboundPPBytes.readByteArray(packetSize.toInt())
                        .asUByteArray()
                val packet = try {
                    PebblePacket.deserialize(packetBytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
//                                Logger.w("error deserializing packet: $packetBytes", e)
                    null
                }
                logger.d("inbound pebble protocol packet: $packet")
                if (packet != null) {
                    dispatch(InboundPPMessage(packet, packetBytes))
                }
            }
        } catch (e: IOException) {
            logger.e("error decoding PP", e)
        }
    }

    // Dispatch must never block indefinitely: that stops us draining inboundPPBytes,
    // which wedges the PPoG loop bidirectionally. Throws to force a disconnect instead.
    internal suspend fun dispatch(message: InboundPPMessage) {
        if (!pebbleProtocolStreams.inboundMessagesFlow.tryEmit(message)) {
            logger.w("inbound dispatch buffer full; waiting for slow subscriber")
            withTimeoutOrNull(DISPATCH_STALL_TIMEOUT) {
                pebbleProtocolStreams.inboundMessagesFlow.emit(message)
            } ?: error("inbound dispatch stalled for $DISPATCH_STALL_TIMEOUT")
        }
    }

    companion object {
        private val PP_HEADER_SIZE: UShort = 4u
        private val DISPATCH_STALL_TIMEOUT = 5.seconds
    }
}
