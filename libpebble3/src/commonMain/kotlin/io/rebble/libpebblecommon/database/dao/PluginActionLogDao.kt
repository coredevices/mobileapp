package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.rebble.libpebblecommon.database.entity.PluginActionLogEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface PluginActionLogDao {
    @Insert
    suspend fun insert(row: PluginActionLogEntity)

    @Query(
        """DELETE FROM PluginActionLog WHERE appUuid = :appUuid AND id NOT IN
        (SELECT id FROM PluginActionLog WHERE appUuid = :appUuid ORDER BY atMs DESC, id DESC LIMIT :keep)"""
    )
    suspend fun trim(appUuid: Uuid, keep: Int)

    /** Inserts [row], dropping its app's oldest beyond [keepPerApp]. */
    @Transaction
    suspend fun insertKeeping(row: PluginActionLogEntity, keepPerApp: Int) {
        insert(row)
        trim(row.appUuid, keepPerApp)
    }

    /** Null filters match everything. */
    @Query(
        """SELECT * FROM PluginActionLog
        WHERE (:pluginUuid IS NULL OR pluginUuid = :pluginUuid) AND (:appUuid IS NULL OR appUuid = :appUuid)
        ORDER BY atMs DESC, id DESC"""
    )
    fun flow(pluginUuid: Uuid?, appUuid: Uuid?): Flow<List<PluginActionLogEntity>>

    @Query("DELETE FROM PluginActionLog WHERE atMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}
