package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*

internal class AppLogShippingControlMessage(enable: Boolean) : PebblePacket(ProtocolEndpoint.APP_LOGS) {
    private val enable = SBoolean(m, enable)
}

internal class AppLogReceivedMessage() : PebblePacket(ProtocolEndpoint.APP_LOGS) {
    val uuid: SUUID = SUUID(m)
    val timestamp: SUInt = SUInt(m)
    val level: SUByte = SUByte(m)
    val messageLength: SUByte = SUByte(m)
    val lineNumber: SUShort = SUShort(m)
    val filename: SFixedString = SFixedString(m, 16)
    val message: SFixedString = SFixedString(m, 0)

    init {
        message.linkWithSize(messageLength)
    }
}

internal fun appLogPacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.APP_LOGS) { AppLogReceivedMessage() }
}