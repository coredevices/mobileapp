package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SOptional
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian

/**
 * Meta endpoint (0x00, spelled [ProtocolEndpoint.RECOVERY]). The watch answers here when it will
 * not answer on the endpoint that was addressed: an [error] saying why, and the [rejectedEndpoint]
 * the refused request was for. A recovery firmware refuses nearly every endpoint, so there this is
 * the only reply most requests get.
 */
class MetaMessage : PebblePacket(endpoint) {
    /**
     * Why the request was refused. See [Error].
     */
    val error = SUByte(m)

    /**
     * The endpoint the refused request was addressed to. Absent for [Error.CorruptedMessage],
     * which the watch cannot attribute to an endpoint.
     */
    val rejectedEndpoint =
        SOptional(m, SUShort(StructMapper(), endianness = Endian.Big), present = true)

    enum class Error(val value: UByte) {
        NoError(0x00u),
        CorruptedMessage(0xd0u),
        Unhandled(0xdcu),
        Disallowed(0xddu),
        ;

        companion object {
            fun fromValue(value: UByte): Error? = entries.firstOrNull { it.value == value }
        }
    }

    override fun toString(): String {
        val name = Error.fromValue(error.get())?.name ?: "0x${error.get().toInt().toString(16)}"
        return "MetaMessage(error=$name, rejectedEndpoint=${rejectedEndpoint.get()})"
    }

    companion object {
        val endpoint = ProtocolEndpoint.RECOVERY
    }
}

fun metaPacketsRegister() {
    PacketRegistry.register(MetaMessage.endpoint) { MetaMessage() }
}
