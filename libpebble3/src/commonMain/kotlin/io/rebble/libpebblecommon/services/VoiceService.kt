package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.packets.OutgoingVoicePacket
import io.rebble.libpebblecommon.packets.SessionSetupCommand
import io.rebble.libpebblecommon.packets.SessionType
import io.rebble.libpebblecommon.packets.VoiceAttribute
import io.rebble.libpebblecommon.packets.VoiceAttributeType
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class VoiceService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService {

    val sessionSetupRequests: Flow<VoiceService.SessionSetupRequest> = protocolHandler.inboundMessages
        .filterIsInstance<SessionSetupCommand>()
        .map {
            val uuidData = it.attributes.firstOrNull { attr ->
                attr.id.get() == VoiceAttributeType.AppUuid.value
            }?.content?.get()

            val uuid = if (uuidData != null) {
                val uuidAttr = VoiceAttribute.AppUuid()
                uuidAttr.fromBytes(DataBuffer(uuidData))
                uuidAttr.uuid.get()
            } else {
                Uuid.NIL
            }
            SessionSetupRequest(
                appUuid = uuid,
                sessionId = it.sessionId.get().toInt(),
                sessionType = SessionType.entries.first {
                        type -> type.value == it.sessionType.get()
                },
                encoderInfo = VoiceEncoderInfo.fromProtocol(it.attributes)
            )
        }

    suspend fun send(packet: OutgoingVoicePacket) {
        protocolHandler.send(packet)
    }

    data class SessionSetupRequest(
        val appUuid: Uuid,
        val sessionId: Int,
        val sessionType: SessionType,
        val encoderInfo: VoiceEncoderInfo?,
    )
}