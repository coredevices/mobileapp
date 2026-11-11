package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.rebble.libpebblecommon.database.entity.AppDataUsageEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface AppDataUsageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: AppDataUsageEntity)

    @Query(
        """UPDATE AppDataUsage
        SET requests = requests + :requests, sentBytes = sentBytes + :sentBytes,
            receivedBytes = receivedBytes + :receivedBytes, declared = :declared
        WHERE appUuid = :appUuid AND permission = :permission AND day = :day AND connection = :connection"""
    )
    suspend fun increment(
        appUuid: Uuid,
        permission: String,
        day: Int,
        connection: String,
        declared: Boolean,
        requests: Long,
        sentBytes: Long,
        receivedBytes: Long,
    )

    /** Adds [usage]'s counts to its row. */
    @Transaction
    suspend fun add(usage: AppDataUsageEntity) {
        insertIfAbsent(usage.copy(requests = 0, sentBytes = 0, receivedBytes = 0))
        with(usage) { increment(appUuid, permission, day, connection, declared, requests, sentBytes, receivedBytes) }
    }

    @Query("SELECT * FROM AppDataUsage WHERE day >= :fromDay")
    fun flowSince(fromDay: Int): Flow<List<AppDataUsageEntity>>

    @Query("DELETE FROM AppDataUsage WHERE day < :beforeDay")
    suspend fun deleteBefore(beforeDay: Int)
}
