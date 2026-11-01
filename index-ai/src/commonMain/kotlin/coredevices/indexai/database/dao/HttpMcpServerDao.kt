package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpMcpServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: HttpMcpServerEntity): Long

    @Delete
    suspend fun deleteServer(server: HttpMcpServerEntity)

    @Query("UPDATE HttpMcpServerEntity SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM HttpMcpServerEntity")
    fun getAllFlow(): Flow<List<HttpMcpServerEntity>>
}
