package io.rebble.libpebblecommon.database.entity

import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.time.Clock

@GenerateRoomEntity(
    primaryKey = "key",
    databaseId = BlobDatabase.HealthStats,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class HealthStat(
    val key: String,
    val payload: ByteArray,
    val lastUpdated: Long = Clock.System.now().toEpochMilliseconds(),
) : BlobDbItem {
    override fun key(): UByteArray =
        key.encodeToByteArray().toUByteArray()

    override fun value(params: ValueParams): UByteArray? =
    payload.toUByteArray()

    override fun recordHashCode(): Int =
        key.hashCode() + payload.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HealthStat

        if (key != other.key) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
