package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.packets.AppReorderOutgoingPacket

internal class AppReorderService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService {
    suspend fun send(packet: AppReorderOutgoingPacket) {
        protocolHandler.send(packet)
    }
}