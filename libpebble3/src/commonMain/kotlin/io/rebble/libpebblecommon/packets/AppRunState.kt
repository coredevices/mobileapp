package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUUID
import kotlin.uuid.Uuid


internal sealed class AppRunStateMessage(message: Message) : PebblePacket(endpoint) {
    val command: SUByte = SUByte(m, message.value)

    init {
        type = command.get()
    }

    enum class Message(val value: UByte) {
        AppRunStateStart(0x01u),
        AppRunStateStop(0x02u),
        AppRunStateRequest(0x03u)
    }

    class AppRunStateStart(
        uuid: Uuid = Uuid.NIL
    ) :
        AppRunStateMessage(Message.AppRunStateStart) {
        val uuid: SUUID = SUUID(m, uuid)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AppRunStateStart) return false
            if (!super.equals(other)) return false

            if (uuid != other.uuid) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + uuid.hashCode()
            return result
        }

        override fun toString(): String {
            return "AppRunStateStart(uuid=$uuid)"
        }
    }

    class AppRunStateStop(
        uuid: Uuid = Uuid.NIL
    ) :
        AppRunStateMessage(Message.AppRunStateStop) {
        val uuid: SUUID = SUUID(m, uuid)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AppRunStateStop) return false
            if (!super.equals(other)) return false

            if (uuid != other.uuid) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + uuid.hashCode()
            return result
        }

        override fun toString(): String {
            return "AppRunStateStop(uuid=$uuid)"
        }
    }

    class AppRunStateRequest : AppRunStateMessage(Message.AppRunStateRequest)

    companion object {
        val endpoint: ProtocolEndpoint = ProtocolEndpoint.APP_RUN_STATE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppRunStateMessage) return false

        if (command != other.command) return false

        return true
    }

    override fun hashCode(): Int {
        return command.hashCode()
    }
}

internal fun appRunStatePacketsRegister() {
    PacketRegistry.register(
        AppRunStateMessage.endpoint,
        AppRunStateMessage.Message.AppRunStateStart.value
    ) { AppRunStateMessage.AppRunStateStart() }

    PacketRegistry.register(
        AppRunStateMessage.endpoint,
        AppRunStateMessage.Message.AppRunStateStop.value
    ) { AppRunStateMessage.AppRunStateStop() }

    PacketRegistry.register(
        AppRunStateMessage.endpoint,
        AppRunStateMessage.Message.AppRunStateRequest.value
    ) { AppRunStateMessage.AppRunStateRequest() }
}