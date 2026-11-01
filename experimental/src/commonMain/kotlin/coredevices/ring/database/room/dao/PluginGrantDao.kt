package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.PluginGrantDenial
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginGrantDao {
    @Query("SELECT * FROM PluginGrantDenial")
    fun getAllFlow(): Flow<List<PluginGrantDenial>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(denial: PluginGrantDenial)

    @Delete
    suspend fun delete(denial: PluginGrantDenial)
}
