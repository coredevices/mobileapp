package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface McpSandboxGroupDao {
    @Insert
    suspend fun insertGroup(group: McpSandboxGroupEntity): Long

    @Query("SELECT * FROM McpSandboxGroupEntity")
    fun getAllFlow(): Flow<List<McpSandboxGroupEntity>>

    @Query("UPDATE McpSandboxGroupEntity SET modelType = :modelType WHERE id = :groupId")
    suspend fun updateModelType(groupId: Long, modelType: SandboxModelType)

    @Query("SELECT * FROM McpSandboxGroupEntity WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: Long): McpSandboxGroupEntity?

    @Query("DELETE FROM McpSandboxGroupEntity WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)
}