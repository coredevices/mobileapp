package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.indexai.data.entity.mcp.DisabledBuiltinMcp
import kotlinx.coroutines.flow.Flow

@Dao
interface DisabledBuiltinMcpDao {
    @Query("SELECT * FROM DisabledBuiltinMcp")
    fun getAllFlow(): Flow<List<DisabledBuiltinMcp>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(disabled: DisabledBuiltinMcp)

    @Delete
    suspend fun delete(disabled: DisabledBuiltinMcp)
}
