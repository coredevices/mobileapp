package io.rebble.libpebblecommon.database.dao

import androidx.room.Upsert
import coredev.BlobDatabase
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface BlobDbDao<T : BlobDbRecord> {
    // Compiler will choke on these methods unless they are overridden in each Dao
    fun dirtyRecordsForWatchInsert(transport: String, timestampMs: Long, insertOnlyAfterMs: Long = -1): Flow<List<T>>
    fun dirtyRecordsForWatchDelete(transport: String, timestampMs: Long): Flow<List<T>>
    suspend fun markSyncedToWatch(
        transport: String,
        item: T,
        hashcode: Int,
    )
    suspend fun markDeletedFromWatch(
        transport: String,
        item: T,
        hashcode: Int,
    )
    fun existsOnWatch(transport: String, item: T): Flow<Boolean>
    fun databaseId(): BlobDatabase
    @Upsert
    suspend fun insertOrReplace(item: T)
    @Upsert
    suspend fun insertOrReplaceAll(items: List<T>)
    suspend fun markAllDeletedFromWatch(transport: String)
    suspend fun handleWrite(write: DbWrite, transport: String, params: ValueParams): BlobResponse.BlobStatus = BlobResponse.BlobStatus.Success
    // TODO decide how to handle records which are synced to watches which haven't been connected
    //  for a while (so that we aren't storing them forever if the watch is never connected again).
    suspend fun deleteStaleRecords(timestampMs: Long)
    suspend fun deleteSyncRecordsForDevicesWhichDontExist()
}

interface BlobDbRecord {
    val recordHashcode: Int
    val deleted: Boolean
    val record: BlobDbItem
}

data class ValueParams(
    val platform: WatchType,
    val capabilities: Set<ProtocolCapsFlag>,
    val vibePatternDao: VibePatternDao? = null,
)

interface BlobDbItem {
    fun key(): UByteArray
    fun value(params: ValueParams): UByteArray?
    fun recordHashCode(): Int
    /** Only needed for tables which use insert-with-timestamp */
    fun timestamp(): Instant? = null
}
