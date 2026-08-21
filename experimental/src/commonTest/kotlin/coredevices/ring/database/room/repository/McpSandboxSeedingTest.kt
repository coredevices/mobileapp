package coredevices.ring.database.room.repository

import coredevices.indexai.data.entity.mcp_sandbox.BuiltinMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.indexai.database.dao.BuiltinMcpGroupAssociationDao
import coredevices.indexai.database.dao.McpSandboxGroupDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class McpSandboxSeedingTest {

    private val builtins = listOf("builtin_note", "builtin_reminder", "builtin_calendar")

    private class FakeGroupDao : McpSandboxGroupDao {
        val groups = mutableListOf<McpSandboxGroupEntity>()
        override suspend fun insertGroup(group: McpSandboxGroupEntity): Long {
            val id = groups.size + 1L
            groups += group.copy(id = id)
            return id
        }
        override fun getAllFlow(): Flow<List<McpSandboxGroupEntity>> = flowOf(groups.toList())
        override suspend fun getAll(): List<McpSandboxGroupEntity> = groups.toList()
        override suspend fun updateModelType(groupId: Long, modelType: SandboxModelType) {
            groups.replaceAll { if (it.id == groupId) it.copy(modelType = modelType) else it }
        }
        override suspend fun getGroupById(groupId: Long): McpSandboxGroupEntity? =
            groups.firstOrNull { it.id == groupId }
        override suspend fun deleteGroup(groupId: Long) { groups.removeAll { it.id == groupId } }
    }

    private class FakeAssociationDao : BuiltinMcpGroupAssociationDao {
        val associations = mutableListOf<BuiltinMcpGroupAssociation>()
        override suspend fun insertAssociation(association: BuiltinMcpGroupAssociation): Long {
            associations += association
            return associations.size.toLong()
        }
        override suspend fun insertAssociations(
            associations: List<BuiltinMcpGroupAssociation>
        ): List<Long> = associations.map { insertAssociation(it) }
        override suspend fun deleteAssociation(association: BuiltinMcpGroupAssociation) {
            associations.remove(association)
        }
        override fun getAssociationsForGroupFlow(
            groupId: Long
        ): Flow<List<BuiltinMcpGroupAssociation>> =
            flowOf(associations.filter { it.groupId == groupId })
        override suspend fun getGroupIdsForBuiltin(builtinMcpName: String): List<Long> =
            associations.filter { it.builtinMcpName == builtinMcpName }.map { it.groupId }
    }

    @Test
    fun firstLaunchCreatesTheDefaultGroupWithEveryBuiltin() = runBlocking {
        val groupDao = FakeGroupDao()
        val associationDao = FakeAssociationDao()

        seedDefaultGroup(groupDao, associationDao, builtins)

        assertEquals(1, groupDao.groups.size)
        assertEquals(
            builtins,
            associationDao.associations
                .filter { it.groupId == groupDao.groups.single().id }
                .map { it.builtinMcpName },
        )
    }

    @Test
    fun seedingIsSkippedOnceAGroupExistsSoDisabledBuiltinsStayDisabled() = runBlocking {
        val groupDao = FakeGroupDao()
        val associationDao = FakeAssociationDao()
        val groupId = groupDao.insertGroup(McpSandboxGroupEntity(title = "Default MCP Sandbox"))
        associationDao.insertAssociation(BuiltinMcpGroupAssociation(groupId, "builtin_note"))

        seedDefaultGroup(groupDao, associationDao, builtins)

        assertEquals(1, groupDao.groups.size)
        assertEquals(listOf("builtin_note"), associationDao.associations.map { it.builtinMcpName })
    }
}
