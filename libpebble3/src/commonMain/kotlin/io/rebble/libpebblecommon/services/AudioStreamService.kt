package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.packets.AudioStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.takeWhile

internal class AudioStreamService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService {
    suspend fun send(packet: AudioStream) {
        protocolHandler.send(packet)
    }

    fun dataFlowForSession(sessionId: UShort): Flow<AudioStream.DataTransfer> =
        protocolHandler.inboundMessages.filterIsInstance<AudioStream>()
            .filter { it.sessionId.get() == sessionId }
            .takeWhile { it !is AudioStream.StopTransfer }
            .filterIsInstance<AudioStream.DataTransfer>()
}