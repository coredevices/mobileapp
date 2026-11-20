package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

internal sealed class HealthSyncOutgoingPacket : PebblePacket(ProtocolEndpoint.HEALTH_SYNC) {
    class RequestSync(timeSinceLastSync: UInt) : HealthSyncOutgoingPacket() {
        val command: SUByte = SUByte(m, 1u)
        val timeSince: SUInt = SUInt(m, timeSinceLastSync, endianness = Endian.Little)
    }
}

internal enum class HealthCommand(val code: UByte) {
    Sync(0x01u),
    Response(0x11u),
}

/**
 * Raw incoming packet used when the watch requests a health sync.
 * The payload is left uninterpreted so we accept any watch-side command.
 */
internal class HealthSyncIncomingPacket : PebblePacket(ProtocolEndpoint.HEALTH_SYNC) {
    val command: SUByte = SUByte(m)
    val result: SUByte = SUByte(m)
}

internal enum class HealthResult(val code: UByte) {
    Ack(0x01u),
    Nack(0x02u),
}

internal fun healthSyncPacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.HEALTH_SYNC) {
        HealthSyncIncomingPacket()
    }
}
