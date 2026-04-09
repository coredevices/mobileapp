package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpGroupAssociation
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpMcpGroupAssociationDao {
    @Insert(onConflict = IGNORE)
    suspend fun insertAssociation(association: HttpMcpGroupAssociation): Long
    @Delete
    suspend fun deleteAssociation(association: HttpMcpGroupAssociation)
    @Query("SELECT * FROM HttpMcpGroupAssociation WHERE groupId = :groupId")
    fun getAssociationsForGroupFlow(groupId: Long): Flow<List<HttpMcpGroupAssociation>>
}