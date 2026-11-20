package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.packets.AppLogShippingControlMessage

internal class AppLogService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService {
    suspend fun send(packet: AppLogShippingControlMessage) {
        protocolHandler.send(packet)
    }
}