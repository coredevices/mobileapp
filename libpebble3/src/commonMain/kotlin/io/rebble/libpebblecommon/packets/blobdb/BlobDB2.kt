package io.rebble.libpebblecommon.packets.blobdb

import coredev.BlobDatabase
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.DirtyDatabase
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.StartSync
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.SyncDone
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.Write
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.WriteBack
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.Version
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command.Message.MarkAllDirty
import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SFixedList
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian

sealed class BlobDB2Command(message: Message, token: UShort) : PebblePacket(endpoint) {
    companion object {
        val endpoint = ProtocolEndpoint.BLOBDB_V2
    }

    enum class Message(val value: UByte) {
        DirtyDatabase(0x06u), // Phone -> Watch
        StartSync(0x07u), // Phone -> Watch
        Write(0x08u), // Watch -> Phone
        WriteBack(0x09u), // Watch -> Phone
        SyncDone(0x0Au), // Watch -> Phone
        Version(0x0Bu), // Phone -> Watch
        MarkAllDirty(0x0Cu), // Phone -> Watch

        InvalidMessage(0xFFu)
    }

    val command = SUByte(m, message.value)
    val token = SUShort(m, token)

    /**
     * Response: [BlobDB2Response.DirtyDatabaseResponse]
     */
    class DirtyDatabase(token: UShort = 0u) : BlobDB2Command(DirtyDatabase, token)

    /**
     * Response: [BlobDB2Response.StartSyncResponse]
     */
    class StartSync(
        token: UShort = 0u,
        database: BlobDatabase = BlobDatabase.Invalid
    ) : BlobDB2Command(StartSync, token) {
        val database = SUByte(m, database.id)
    }

    /**
     * Response: [BlobDB2Response.WriteResponse]
     */
    class Write(
        token: UShort = 0u,
        database: BlobDatabase = BlobDatabase.Invalid,
        timestamp: UInt = 0u,
        key: UByteArray = ubyteArrayOf(),
        value: UByteArray = ubyteArrayOf()
    ) : BlobDB2Command(Write, token) {
        val database = SUByte(m, database.id)
        val timestamp = SUInt(m, timestamp, endianness = Endian.Little)
        val keySize = SUByte(m, key.size.toUByte())
        val key = SBytes(m, key.size, key).apply {
            linkWithSize(keySize)
        }
        val valueSize = SUShort(m, value.size.toUShort(), endianness = Endian.Little)
        val value = SBytes(m, value.size, value).apply {
            linkWithSize(valueSize)
        }
    }

    /**
     * Response: [BlobDB2Response.WriteBackResponse]
     */
    class WriteBack(
        token: UShort = 0u,
        database: BlobDatabase = BlobDatabase.Invalid,
        timestamp: UInt = 0u,
        key: UByteArray = ubyteArrayOf(),
        value: UByteArray = ubyteArrayOf()
    ) : BlobDB2Command(WriteBack, token) {
        val database = SUByte(m, database.id)
        val timestamp = SUInt(m, timestamp, endianness = Endian.Little)
        val keySize = SUByte(m, key.size.toUByte())
        val key = SBytes(m, key.size, key).apply {
            linkWithSize(keySize)
        }
        val valueSize = SUShort(m, value.size.toUShort(), endianness = Endian.Little)
        val value = SBytes(m, value.size, value).apply {
            linkWithSize(valueSize)
        }
    }

    /**
     * Response: [BlobDB2Response.SyncDoneResponse]
     */
    class SyncDone(
        token: UShort = 0u,
        database: BlobDatabase = BlobDatabase.Invalid
    ) : BlobDB2Command(SyncDone, token) {
        val database = SUByte(m, database.id)
    }

    class Version(
        token: UShort = 0u,
    ) : BlobDB2Command(Message.Version, token)

    class MarkAllDirty(
        token: UShort = 0u,
        database: BlobDatabase = BlobDatabase.Invalid
    ) : BlobDB2Command(Message.MarkAllDirty, token) {
        val database = SUByte(m, database.id)
    }
}

sealed class BlobDB2Response(
    message: Message = Message.InvalidMessage,
    token: UShort = 0u,
    status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure
) : PebblePacket(endpoint) {
    companion object {
        val endpoint = ProtocolEndpoint.BLOBDB_V2
        const val RESPONSE_MESSAGE_OFF: UByte = 0x80u
    }

    val command = SUByte(m, message.value)
    val token = SUShort(m, token)
    val status = SUByte(m, status.value)

    enum class Message(val value: UByte) {
        DirtyDatabaseResponse(RESPONSE_MESSAGE_OFF or DirtyDatabase.value), // Watch -> Phone
        StartSyncResponse(RESPONSE_MESSAGE_OFF or StartSync.value), // Watch -> Phone
        WriteResponse(RESPONSE_MESSAGE_OFF or Write.value), // Phone -> Watch
        WriteBackResponse(RESPONSE_MESSAGE_OFF or WriteBack.value), // Phone -> Watch
        SyncDoneResponse(RESPONSE_MESSAGE_OFF or SyncDone.value), // Phone -> Watch
        VersionResponse(RESPONSE_MESSAGE_OFF or Version.value), // Watch -> Phone
        MarkAllDirtyResponse(RESPONSE_MESSAGE_OFF or MarkAllDirty.value), // Watch -> Phone

        InvalidMessage(0xFFu)
    }

    class DirtyDatabaseResponse(
        token: UShort = 0u,
        status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure,
        dirtyDatabases: Set<BlobDatabase> = emptySet()
    ) : BlobDB2Response(Message.DirtyDatabaseResponse, token, status) {
        val databaseIdCount = SUByte(m, dirtyDatabases.size.toUByte())
        val databaseIds = SFixedList(
            m,
            dirtyDatabases.size,
            dirtyDatabases.map { SUByte(StructMapper(), it.id) }.toList()
        ) {
            SUByte(
                StructMapper()
            )
        }.apply {
            linkWithCount(databaseIdCount)
        }
    }

    class StartSyncResponse(
        token: UShort = 0u,
        status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure
    ) : BlobDB2Response(Message.StartSyncResponse, token, status)

    class WriteResponse(
        token: UShort = 0u,
        status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure
    ) : BlobDB2Response(Message.WriteResponse, token, status)

    class WriteBackResponse(
        token: UShort = 0u,
        status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure
    ) : BlobDB2Response(Message.WriteBackResponse, token, status)

    class SyncDoneResponse(
        token: UShort = 0u,
        status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure
    ) : BlobDB2Response(Message.SyncDoneResponse, token, status)

    class VersionResponseCmd : BlobDB2Response(Message.VersionResponse) {
        val version = SUByte(m)
    }

    class MarkAllDirtyResponse(
        token: UShort = 0u,
        status: BlobResponse.BlobStatus = BlobResponse.BlobStatus.GeneralFailure
    ) : BlobDB2Response(Message.MarkAllDirtyResponse, token, status)
}

fun blobDB2PacketsRegister() {
    PacketRegistry.register(
        BlobDB2Command.endpoint,
        BlobDB2Command.Message.DirtyDatabase.value
    ) { BlobDB2Command.DirtyDatabase() }
    PacketRegistry.register(
        BlobDB2Command.endpoint,
        BlobDB2Command.Message.StartSync.value
    ) { BlobDB2Command.StartSync() }
    PacketRegistry.register(
        BlobDB2Command.endpoint,
        BlobDB2Command.Message.Write.value
    ) { BlobDB2Command.Write() }
    PacketRegistry.register(
        BlobDB2Command.endpoint,
        BlobDB2Command.Message.WriteBack.value
    ) { BlobDB2Command.WriteBack() }
    PacketRegistry.register(
        BlobDB2Command.endpoint,
        BlobDB2Command.Message.SyncDone.value
    ) { BlobDB2Command.SyncDone() }

    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.DirtyDatabaseResponse.value,
    ) { BlobDB2Response.DirtyDatabaseResponse() }
    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.StartSyncResponse.value
    ) { BlobDB2Response.StartSyncResponse() }
    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.WriteResponse.value
    ) { BlobDB2Response.WriteResponse() }
    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.WriteBackResponse.value
    ) { BlobDB2Response.WriteBackResponse() }
    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.SyncDoneResponse.value
    ) { BlobDB2Response.SyncDoneResponse() }
    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.VersionResponse.value
    ) { BlobDB2Response.VersionResponseCmd() }
    PacketRegistry.register(
        BlobDB2Response.endpoint,
        BlobDB2Response.Message.MarkAllDirtyResponse.value
    ) { BlobDB2Response.MarkAllDirtyResponse() }
}