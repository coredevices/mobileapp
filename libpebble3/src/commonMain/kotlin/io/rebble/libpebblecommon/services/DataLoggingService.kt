package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.datalogging.Datalogging
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.DataLoggingIncomingPacket
import io.rebble.libpebblecommon.packets.DataLoggingOutgoingPacket
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.uuid.Uuid

class DataLoggingService(
    private val protocolHandler: PebbleProtocolHandler,
    private val scope: ConnectionCoroutineScope,
    private val datalogging: Datalogging,
) : ProtocolService {
    private var watchInfo: WatchInfo? = null
    private var acceptSessions = false

    companion object {
        private val logger = Logger.withTag("DataLoggingService")
    }

    private val sessions = mutableMapOf<UByte, DataLoggingSession>()

    private suspend fun send(packet: DataLoggingOutgoingPacket) {
        protocolHandler.send(packet)
    }

    private suspend fun sendAckNack(sessionId: UByte) {
        if (acceptSessions) {
            send(DataLoggingOutgoingPacket.ACK(sessionId))
        } else {
            send(DataLoggingOutgoingPacket.NACK(sessionId))
        }
    }

    suspend fun realInit(info: WatchInfo) {
        acceptSessions = true
        watchInfo = info
        send(DataLoggingOutgoingPacket.ReportOpenSessions(emptyList()))
    }

    fun initialInit() {
        protocolHandler.inboundMessages.onEach {
            when (it) {
                is DataLoggingIncomingPacket.OpenSession -> {
                    val id = it.sessionId.get()
                    val tag = it.tag.get()
                    val applicationUuid = it.applicationUUID.get()
                    val itemSize = it.dataItemSize.get()
                    logger.d { "Session opened: $id tag: $tag (accepted: $acceptSessions)" }
                    sessions[id] = DataLoggingSession(id, tag, applicationUuid, itemSize)
                    datalogging.openSession(id, tag, applicationUuid, itemSize)
                    sendAckNack(id)
                }

                is DataLoggingIncomingPacket.SendDataItems -> {
                    val id = it.sessionId.get()
                    val session = sessions[id]
                    if (session == null) {
                        logger.e { "Session not found: $id" }
                        return@onEach
                    }
                    sendAckNack(id)
                    val info = watchInfo
                    if (info == null) {
                        logger.e { "watch info is null" }
                        return@onEach
                    }
                    datalogging.logData(
                        sessionId = id,
                        uuid = session.uuid,
                        tag = session.tag,
                        data = it.payload.get().toByteArray(),
                        watchInfo = info,
                        itemSize = session.itemSize,
                        itemsLeft = it.itemsLeftAfterThis.get(),
                    )
                }

                is DataLoggingIncomingPacket.CloseSession -> {
                    val id = it.sessionId.get()
                    val session = sessions[id]
                    logger.d { "Session closed: $id" }
                    if (session != null) {
                        datalogging.closeSession(id, session.tag)
                    }
                    sessions.remove(id)
                    sendAckNack(id)
                }
            }
        }.launchIn(scope)
    }
}

data class DataLoggingSession(
    val id: UByte,
    val tag: UInt,
    val uuid: Uuid,
    val itemSize: UShort,
)