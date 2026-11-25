package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*

internal sealed class PutBytesOutgoingPacket(command: PutBytesCommand) :
    PebblePacket(ProtocolEndpoint.PUT_BYTES) {
    /**
     * Request command. See [PutBytesCommand].
     */
    val command: SUByte = SUByte(m, command.value)

}

internal class PutBytesResponse : PebblePacket(ProtocolEndpoint.PUT_BYTES) {

    /**
     * See [PutBytesResult]
     */
    val result: SUByte = SUByte(m)

    /**
     * Cookie to send to all other put bytes requests
     */
    val cookie: SUInt = SUInt(m)

    val isAck: Boolean
        get() = result.get() == PutBytesResult.ACK.value
}

/**
 * Send to init non-app related file transfer.
 *
 * [objectSize] is the number of bytes that will be sent in this session — when resuming, that is
 * the total object size minus [resumeOffset].
 */
internal class PutBytesInit(
    objectSize: UInt,
    objectType: ObjectType,
    bank: UByte,
    filename: String,
    resumeOffset: UInt? = null,
) : PutBytesOutgoingPacket(PutBytesCommand.INIT) {
    val objectSize: SUInt = SUInt(m, objectSize)
    val objectType: SUByte = SUByte(m, objectType.value)
    val bank: SUByte = SUByte(m, bank)
    val filename: SOptional<String> = SOptional(m, SNullTerminatedString(StructMapper(), filename), filename.isNotEmpty())
    val resumeMagic: SOptional<UInt> = SOptional(m, SUInt(StructMapper(), RESUME_MAGIC), resumeOffset != null)
    val resumeOffset: SOptional<UInt> = SOptional(m, SUInt(StructMapper(), resumeOffset ?: 0u), resumeOffset != null)

    companion object {
        const val RESUME_MAGIC: UInt = 0xBE4354EFu
    }
}

/**
 * Send to init app-specific file transfer.
 */
internal class PutBytesAppInit(
    objectSize: UInt,
    objectType: ObjectType,
    appId: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.INIT) {
    val objectSize: SUInt = SUInt(m, objectSize)

    // Object type in app init packet must have 8th bit set (?)
    val objectType: SUByte = SUByte(m, objectType.value or (1u shl 7).toUByte())
    val appId: SUInt = SUInt(m, appId)
}

/**
 * Send file data to the watch. After every put you have to wait for response from the watch.
 */
internal class PutBytesPut(
    cookie: UInt,
    payload: UByteArray
) : PutBytesOutgoingPacket(PutBytesCommand.PUT) {
    val cookie: SUInt = SUInt(m, cookie)
    val payloadSize: SUInt = SUInt(m, payload.size.toUInt())
    val payload: SBytes = SBytes(m, payload.size, payload)
}

/**
 * Sent when current file transfer is complete. [objectCrc] is the CRC32 hash of the sent payload.
 */
internal class PutBytesCommit(
    cookie: UInt,
    objectCrc: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.COMMIT) {
    val cookie: SUInt = SUInt(m, cookie)
    val objectCrc: SUInt = SUInt(m, objectCrc)
}

/**
 * Send when there was an error during transfer and transfer cannot complete.
 */
internal class PutBytesAbort(
    cookie: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.ABORT) {
    val cookie: SUInt = SUInt(m, cookie)
}

/**
 * Send after app-related file was commited to complete install sequence
 */
internal class PutBytesInstall(
    cookie: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.INSTALL) {
    val cookie: SUInt = SUInt(m, cookie)
}

internal enum class PutBytesCommand(val value: UByte) {
    INIT(0x01u),
    PUT(0x02u),
    COMMIT(0x03u),
    ABORT(0x04u),
    INSTALL(0x05u)
}

internal enum class PutBytesResult(val value: UByte) {
    ACK(0x01u),
    NACK(0x02u)
}

internal enum class ObjectType(val value: UByte) {
    FIRMWARE(0x01u),
    RECOVERY(0x02u),
    SYSTEM_RESOURCE(0x03u),
    APP_RESOURCE(0x04u),
    APP_EXECUTABLE(0x05u),
    FILE(0x06u),
    WORKER(0x07u)
}

internal fun putBytesIncomingPacketsRegister() {
    PacketRegistry.register(
        ProtocolEndpoint.PUT_BYTES,
        PutBytesResult.ACK.value,
    ) { PutBytesResponse() }

    PacketRegistry.register(
        ProtocolEndpoint.PUT_BYTES,
        PutBytesResult.NACK.value,
    ) { PutBytesResponse() }
}