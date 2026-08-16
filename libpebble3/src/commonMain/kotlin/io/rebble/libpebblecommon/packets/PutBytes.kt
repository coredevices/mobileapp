package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*

sealed class PutBytesOutgoingPacket(command: PutBytesCommand) :
    PebblePacket(ProtocolEndpoint.PUT_BYTES) {
    /**
     * Request command. See [PutBytesCommand].
     */
    val command = SUByte(m, command.value)

}

class PutBytesResponse : PebblePacket(ProtocolEndpoint.PUT_BYTES) {

    /**
     * See [PutBytesResult]
     */
    val result = SUByte(m)

    /**
     * Cookie to send to all other put bytes requests
     */
    val cookie = SUInt(m)

    val isAck: Boolean
        get() = result.get() == PutBytesResult.ACK.value
}

/**
 * Send to init non-app related file transfer.
 *
 * [objectSize] is the number of bytes that will be sent in this session — when resuming, that is
 * the total object size minus [resumeOffset].
 */
class PutBytesInit(
    objectSize: UInt,
    objectType: ObjectType,
    bank: UByte,
    filename: String,
    resumeOffset: UInt? = null,
) : PutBytesOutgoingPacket(PutBytesCommand.INIT) {
    val objectSize = SUInt(m, objectSize)
    val objectType = SUByte(m, objectType.value)
    val bank = SUByte(m, bank)
    val filename = SOptional(m, SNullTerminatedString(StructMapper(), filename), filename.isNotEmpty())
    val resumeMagic = SOptional(m, SUInt(StructMapper(), RESUME_MAGIC), resumeOffset != null)
    val resumeOffset = SOptional(m, SUInt(StructMapper(), resumeOffset ?: 0u), resumeOffset != null)

    companion object {
        const val RESUME_MAGIC: UInt = 0xBE4354EFu
    }
}

/**
 * Send to init app-specific file transfer.
 */
class PutBytesAppInit(
    objectSize: UInt,
    objectType: ObjectType,
    appId: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.INIT) {
    val objectSize = SUInt(m, objectSize)

    // Object type in app init packet must have 8th bit set (?)
    val objectType = SUByte(m, objectType.value or (1u shl 7).toUByte())
    val appId = SUInt(m, appId)
}

/**
 * Send file data to the watch. After every put you have to wait for response from the watch.
 */
class PutBytesPut(
    cookie: UInt,
    payload: UByteArray
) : PutBytesOutgoingPacket(PutBytesCommand.PUT) {
    val cookie = SUInt(m, cookie)
    val payloadSize = SUInt(m, payload.size.toUInt())
    val payload = SBytes(m, payload.size, payload)
}

/**
 * Sent when current file transfer is complete. [objectCrc] is the CRC32 hash of the sent payload.
 */
class PutBytesCommit(
    cookie: UInt,
    objectCrc: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.COMMIT) {
    val cookie = SUInt(m, cookie)
    val objectCrc = SUInt(m, objectCrc)
}

/**
 * Send when there was an error during transfer and transfer cannot complete.
 */
class PutBytesAbort(
    cookie: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.ABORT) {
    val cookie = SUInt(m, cookie)
}

/**
 * Send after app-related file was commited to complete install sequence
 */
class PutBytesInstall(
    cookie: UInt
) : PutBytesOutgoingPacket(PutBytesCommand.INSTALL) {
    val cookie = SUInt(m, cookie)
}

enum class PutBytesCommand(val value: UByte) {
    INIT(0x01u),
    PUT(0x02u),
    COMMIT(0x03u),
    ABORT(0x04u),
    INSTALL(0x05u)
}

enum class PutBytesResult(val value: UByte) {
    ACK(0x01u),
    NACK(0x02u)
}

enum class ObjectType(val value: UByte) {
    FIRMWARE(0x01u),
    RECOVERY(0x02u),
    SYSTEM_RESOURCE(0x03u),
    APP_RESOURCE(0x04u),
    APP_EXECUTABLE(0x05u),
    FILE(0x06u),
    WORKER(0x07u)
}

fun putBytesIncomingPacketsRegister() {
    PacketRegistry.register(
        ProtocolEndpoint.PUT_BYTES,
        PutBytesResult.ACK.value,
    ) { PutBytesResponse() }

    PacketRegistry.register(
        ProtocolEndpoint.PUT_BYTES,
        PutBytesResult.NACK.value,
    ) { PutBytesResponse() }
}