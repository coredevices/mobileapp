package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import coredevices.indexai.data.entity.mcp_sandbox.BuiltinMcpGroupAssociation
import kotlinx.coroutines.flow.Flow

@Dao
interface BuiltinMcpGroupAssociationDao {
    @Insert(onConflict = IGNORE)
    suspend fun insertAssociation(association: BuiltinMcpGroupAssociation): Long

    @Insert(onConflict = IGNORE)
    suspend fun insertAssociations(associations: List<BuiltinMcpGroupAssociation>): List<Long>

    @Delete
    suspend fun deleteAssociation(association: BuiltinMcpGroupAssociation)

    @Query("SELECT * FROM BuiltinMcpGroupAssociation WHERE groupId = :groupId")
    fun getAssociationsForGroupFlow(groupId: Long): Flow<List<BuiltinMcpGroupAssociation>>
}