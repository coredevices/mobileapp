package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.rebble.libpebblecommon.database.entity.VibePatternEntity
import io.rebble.libpebblecommon.notification.DefaultVibePattern
import io.rebble.libpebblecommon.notification.uIntPattern
import kotlinx.coroutines.flow.Flow

@Dao
interface VibePatternDao {
    @Query("""
        SELECT *
        FROM VibePatternEntity
    """)
    fun getVibePatterns(): Flow<List<VibePatternEntity>>

    @Query("""
        SELECT *
        FROM VibePatternEntity
        WHERE name = :name
    """)
    suspend fun getVibePattern(name: String): VibePatternEntity?

    @Insert(onConflict = IGNORE)
    suspend fun insertOrIgnore(pattern: VibePatternEntity)

    @Upsert
    suspend fun insertOrUpdate(pattern: VibePatternEntity)

    @Transaction
    suspend fun ensureAllDefaultsInserted() {
        DefaultVibePattern.entries.forEach { pattern ->
            insertOrUpdate(VibePatternEntity(
                name = pattern.displayName,
                pattern = pattern.uIntPattern(),
                bundled = true,
            ))
        }
    }

    @Query("DELETE FROM VibePatternEntity WHERE name = :name AND bundled = 0")
    suspend fun deleteCustomPattern(name: String)
}