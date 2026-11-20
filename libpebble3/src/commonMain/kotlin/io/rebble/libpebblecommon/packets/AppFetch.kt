package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.util.Endian

internal sealed class AppFetchIncomingPacket() : PebblePacket(ProtocolEndpoint.APP_FETCH) {
    /**
     * Request command. See [AppFetchRequestCommand].
     */
    val command: SUByte = SUByte(m)

}

internal sealed class AppFetchOutgoingPacket(command: AppFetchRequestCommand) :
    PebblePacket(ProtocolEndpoint.APP_FETCH) {
    /**
     * Request command. See [AppFetchRequestCommand].
     */
    val command: SUByte = SUByte(m, command.value)

}


/**
 * Packet sent from the watch when user opens an app that is not in the watch storage.
 */
internal class AppFetchRequest : AppFetchIncomingPacket() {

    /**
     * UUID of the app to request
     */
    val uuid: SUUID = SUUID(m)

    /**
     * ID of the app bank. Use in the [PutBytesAppInit] packet to identify this app install.
     */
    val appId: SUInt = SUInt(m, endianness = Endian.Little)
}

/**
 * Packet sent from the watch when user opens an app that is not in the watch storage.
 */
internal class AppFetchResponse(
    status: AppFetchResponseStatus
) : AppFetchOutgoingPacket(AppFetchRequestCommand.FETCH_APP) {
    /**
     * Response status
     */
    val status: SUByte = SUByte(m, status.value)

}

internal enum class AppFetchRequestCommand(val value: UByte) {
    FETCH_APP(0x01u)
}

internal enum class AppFetchResponseStatus(val value: UByte) {
    /**
     * Sent right before starting to send PutBytes data
     */
    START(0x01u),

    /**
     * Sent when phone PutBytes is already busy sending something else
     */
    BUSY(0x02u),

    /**
     * Sent when UUID that watch sent is not in the locker
     */
    INVALID_UUID(0x03u),

    /**
     * Sent when there is generic data sending error (such as failure to read the local pbw file)
     */
    NO_DATA(0x01u),
}


internal fun appFetchIncomingPacketsRegister() {
    PacketRegistry.register(
        ProtocolEndpoint.APP_FETCH,
        AppFetchRequestCommand.FETCH_APP.value
    ) { AppFetchRequest() }
}