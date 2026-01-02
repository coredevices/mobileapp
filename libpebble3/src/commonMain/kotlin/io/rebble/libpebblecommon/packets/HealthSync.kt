package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian

sealed class HealthSyncOutgoingPacket : PebblePacket(ProtocolEndpoint.HEALTH_SYNC) {
    class RequestFirstSync(currentTime: UInt) : HealthSyncOutgoingPacket() {
        val command = SUByte(m, 1u)
        val time = SUInt(m, currentTime, endianness = Endian.Little)
    }

    class RequestSync(timeSinceLastSync: UInt) : HealthSyncOutgoingPacket() {
        val command = SUByte(m, 1u)
        val timeSince = SUInt(m, timeSinceLastSync, endianness = Endian.Little)
    }
}

/**
 * Raw incoming packet used when the watch requests a health sync.
 * The payload is left uninterpreted so we accept any watch-side command.
 */
class HealthSyncIncomingPacket(packet: UByteArray) : PebblePacket(packet) {
    val payload: UByteArray

    init {
        val buf = DataBuffer(packet)
        buf.getUShort() // length
        buf.getUShort() // endpoint
        payload = buf.getBytes(buf.remaining).toUByteArray()
    }
}

fun healthSyncPacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.HEALTH_SYNC) { packet ->
        HealthSyncIncomingPacket(packet)
    }
}
