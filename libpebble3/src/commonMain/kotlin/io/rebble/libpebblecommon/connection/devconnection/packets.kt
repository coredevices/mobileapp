package io.rebble.libpebblecommon.connection.devconnection

import io.rebble.libpebblecommon.structmapper.SByte
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SLongString
import io.rebble.libpebblecommon.structmapper.SString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.StructMappable

class ConnectionStatusUpdateMessage(
    connected: Boolean
): StructMappable() {
    val type = SByte(m, ServerMessageType.ConnectionStatusUpdate.value)
    val connected = SUByte(m, if (connected) 0xFFu else 0u)
}

class PhoneAppLogMessage(
    message: String
): StructMappable() {
    val type = SByte(m, ServerMessageType.PhoneAppLog.value)
    val message = SFixedString(m, message.length, message)
}

class InstallStatusMessage(
    success: Boolean
): StructMappable() {
    val type = SByte(m, ServerMessageType.InstallStatus.value)
    val status = SUInt(m, if (success) 0u else 1u)
}

class ProxyAuthenticationMessage(
    token: String
): StructMappable() {
    val type = SByte(m, ServerMessageType.ProxyAuthentication.value)
    val token = SString(m, token)
}

class ProxyAuthenticationMessageV2(
    token: String
): StructMappable() {
    val type = SByte(m, ServerMessageType.ProxyAuthenticationV2.value)
    val token = SLongString(m, token)
}