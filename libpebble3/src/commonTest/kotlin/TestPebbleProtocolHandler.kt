import io.rebble.libpebblecommon.PacketPriority
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class TestPebbleProtocolHandler(
    private val onPacket: suspend TestPebbleProtocolHandler.(PebblePacket) -> Unit,
) : PebbleProtocolHandler {
    private val _inboundMessages = MutableSharedFlow<PebblePacket>()
    override val inboundMessages: SharedFlow<PebblePacket> = _inboundMessages.asSharedFlow()
    override val rawInboundMessages get() = TODO()

    suspend fun receivePacket(message: PebblePacket) = _inboundMessages.emit(message)

    override suspend fun send(message: PebblePacket, priority: PacketPriority) {
        onPacket(message)
    }

    override suspend fun send(message: ByteArray, priority: PacketPriority) {
        TODO("Not yet implemented")
    }
}