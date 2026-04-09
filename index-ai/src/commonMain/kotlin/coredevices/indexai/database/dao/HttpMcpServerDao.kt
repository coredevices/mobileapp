package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpMcpServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: HttpMcpServerEntity): Long

    @Delete
    suspend fun deleteServer(server: HttpMcpServerEntity)

    @Query("SELECT * FROM HttpMcpServerEntity WHERE id IN (SELECT httpMcpId FROM HttpMcpGroupAssociation WHERE groupId = :groupId)")
    fun getAllByGroupId(groupId: Long): Flow<List<HttpMcpServerEntity>>
}
