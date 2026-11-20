package io.rebble.libpebblecommon.database.dao

import androidx.room.Upsert
import coredev.BlobDatabase
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

public interface BlobDbDao<T : BlobDbRecord> {
    // Compiler will choke on these methods unless they are overridden in each Dao
    public fun dirtyRecordsForWatchInsert(transport: String, timestampMs: Long, insertOnlyAfterMs: Long = -1): Flow<List<T>>
    public fun dirtyRecordsForWatchDelete(transport: String, timestampMs: Long): Flow<List<T>>
    public suspend fun markSyncedToWatch(
        transport: String,
        item: T,
        hashcode: Int,
    )
    public suspend fun markDeletedFromWatch(
        transport: String,
        item: T,
        hashcode: Int,
    )
    public fun existsOnWatch(transport: String, item: T): Flow<Boolean>
    public fun databaseId(): BlobDatabase
    @Upsert
    public suspend fun insertOrReplace(item: T)
    @Upsert
    public suspend fun insertOrReplaceAll(items: List<T>)
    public suspend fun markAllDeletedFromWatch(transport: String)
    public suspend fun handleWrite(write: DbWrite, transport: String, params: ValueParams): BlobResponse.BlobStatus = BlobResponse.BlobStatus.Success
    // TODO decide how to handle records which are synced to watches which haven't been connected
    //  for a while (so that we aren't storing them forever if the watch is never connected again).
    public suspend fun deleteStaleRecords(timestampMs: Long)
    public suspend fun deleteSyncRecordsForDevicesWhichDontExist()
}

public interface BlobDbRecord {
    public val recordHashcode: Int
    public val deleted: Boolean
    public val record: BlobDbItem
}

public data class ValueParams internal constructor(
    val platform: WatchType,
    val capabilities: Set<ProtocolCapsFlag>,
    val firmwareVersion: FirmwareVersion,
    val vibePatternDao: VibePatternDao? = null,
    val notificationRuleDao: NotificationRuleDao? = null,
    val libPebbleConfigFlow: LibPebbleConfigFlow,
)

public interface BlobDbItem {
    public fun key(): UByteArray
    public fun value(params: ValueParams): UByteArray?
    /** When true, the item is marked synced without being sent to the watch. */
    public fun skipForWatch(params: ValueParams): Boolean = false
    public fun recordHashCode(): Int
    /** Only needed for tables which use insert-with-timestamp */
    public fun timestamp(): Instant? = null
}
