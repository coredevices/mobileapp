package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McpSandboxGroupDao {
    @Insert
    suspend fun insertGroup(group: McpSandboxGroupEntity): Long

    @Query("SELECT * FROM McpSandboxGroupEntity")
    fun getAllFlow(): Flow<List<McpSandboxGroupEntity>>
}