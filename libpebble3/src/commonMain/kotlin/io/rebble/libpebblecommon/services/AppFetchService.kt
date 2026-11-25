package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AppFetchIncomingPacket
import io.rebble.libpebblecommon.packets.AppFetchOutgoingPacket
import io.rebble.libpebblecommon.packets.AppFetchResponse
import io.rebble.libpebblecommon.packets.AppFetchResponseStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class AppFetchService(
    private val protocolHandler: PebbleProtocolHandler,
    private val scope: ConnectionCoroutineScope,
) : ProtocolService {
    val receivedMessages: Channel<AppFetchIncomingPacket> = Channel<AppFetchIncomingPacket>(Channel.BUFFERED)

    fun init() {
        scope.launch {
            protocolHandler.inboundMessages.collect {
                if (it is AppFetchIncomingPacket) {
                    receivedMessages.trySend(it)
                }
            }
        }
    }

    suspend fun send(packet: AppFetchOutgoingPacket) {
        protocolHandler.send(packet)
    }

    suspend fun sendResponse(result: AppFetchResponseStatus) {
        send(AppFetchResponse(result))
    }
}