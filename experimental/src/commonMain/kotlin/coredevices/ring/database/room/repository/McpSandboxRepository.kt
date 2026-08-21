package coredevices.ring.database.room.repository

import androidx.room.Transactor
import androidx.room.useWriterConnection
import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.entity.mcp_sandbox.BuiltinMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.indexai.database.dao.BuiltinMcpGroupAssociationDao
import coredevices.indexai.database.dao.HttpMcpGroupAssociationDao
import coredevices.indexai.database.dao.HttpMcpServerDao
import coredevices.indexai.database.dao.McpSandboxGroupDao
import coredevices.ring.database.room.RingDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class McpSandboxRepository(
    private val groupDao: McpSandboxGroupDao,
    private val builtinAssociationDao: BuiltinMcpGroupAssociationDao,
    private val httpMcpServerDao: HttpMcpServerDao,
    private val httpMcpGroupAssociationDao: HttpMcpGroupAssociationDao,
    private val builtinMcpRepository: ServletRepository,
    private val db: RingDatabase,
) {
    fun getAllGroupsFlow() = groupDao.getAllFlow()

    fun getDefaultGroupFlow(): Flow<McpSandboxGroupEntity?> =
        groupDao.getAllFlow().map { it.firstOrNull() }

    suspend fun getDefaultGroupId(): Long {
        return groupDao.getAllFlow().first().first().id
    }

    suspend fun updateGroupModelType(groupId: Long, modelType: SandboxModelType) {
        groupDao.updateModelType(groupId, modelType)
    }

    suspend fun createGroup(title: String): Long {
        return groupDao.insertGroup(McpSandboxGroupEntity(title = title))
    }

    suspend fun deleteGroup(groupId: Long) {
        // Associations are removed by FK cascade; servers themselves are kept.
        groupDao.deleteGroup(groupId)
    }

    suspend fun getGroupById(groupId: Long): McpSandboxGroupEntity? {
        return groupDao.getGroupById(groupId)
    }

    fun getMcpServerEntriesForGroup(groupId: Long): Flow<List<McpServerEntry>> {
        return combine(
            builtinAssociationDao.getAssociationsForGroupFlow(groupId).map {
                it.map { McpServerEntry.BuiltinMcpEntry(it.builtinMcpName) }
            },
            httpMcpServerDao.getAllByGroupId(groupId).map {
                it.map { McpServerEntry.HttpServerEntry(it) }
            }
        ) { builtinAssociations, httpEntities ->
            builtinAssociations + httpEntities
        }
    }

    /** All known servers regardless of group membership: every builtin plus every HTTP server. */
    fun getAllServerEntriesFlow(): Flow<List<McpServerEntry>> {
        return httpMcpServerDao.getAllFlow().map { servers ->
            builtinMcpRepository.getAllServlets().map { McpServerEntry.BuiltinMcpEntry(it.name) } +
                servers.map { McpServerEntry.HttpServerEntry(it) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun defaultGroupEntriesFlow(): Flow<List<McpServerEntry>> =
        getDefaultGroupFlow().flatMapLatest { group ->
            if (group == null) flowOf(emptyList()) else getMcpServerEntriesForGroup(group.id)
        }

    suspend fun setBuiltinEnabledInDefaultGroup(builtinName: String, enabled: Boolean) {
        val entry = McpServerEntry.BuiltinMcpEntry(builtinName)
        val defaultGroupId = getDefaultGroupId()
        val groups = getGroupIdsForEntry(entry)
        setGroupsForEntry(entry, if (enabled) groups + defaultGroupId else groups - defaultGroupId)
    }

    suspend fun getGroupIdsForEntry(entry: McpServerEntry): Set<Long> {
        return when (entry) {
            is McpServerEntry.BuiltinMcpEntry ->
                builtinAssociationDao.getGroupIdsForBuiltin(entry.builtinMcpName)
            is McpServerEntry.HttpServerEntry ->
                httpMcpGroupAssociationDao.getGroupIdsForServer(entry.server.id)
        }.toSet()
    }

    suspend fun setGroupsForEntry(entry: McpServerEntry, groupIds: Set<Long>) {
        db.useWriterConnection {
            it.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                applyGroupsForEntry(entry, groupIds)
            }
        }
    }

    private suspend fun applyGroupsForEntry(entry: McpServerEntry, groupIds: Set<Long>) {
        val current = getGroupIdsForEntry(entry)
        val toAdd = groupIds - current
        val toRemove = current - groupIds
        when (entry) {
            is McpServerEntry.BuiltinMcpEntry -> {
                toAdd.forEach {
                    builtinAssociationDao.insertAssociation(
                        BuiltinMcpGroupAssociation(groupId = it, builtinMcpName = entry.builtinMcpName)
                    )
                }
                toRemove.forEach {
                    builtinAssociationDao.deleteAssociation(
                        BuiltinMcpGroupAssociation(groupId = it, builtinMcpName = entry.builtinMcpName)
                    )
                }
            }
            is McpServerEntry.HttpServerEntry -> {
                toAdd.forEach {
                    httpMcpGroupAssociationDao.insertAssociation(
                        HttpMcpGroupAssociation(groupId = it, httpMcpId = entry.server.id)
                    )
                }
                toRemove.forEach {
                    httpMcpGroupAssociationDao.deleteAssociation(
                        HttpMcpGroupAssociation(groupId = it, httpMcpId = entry.server.id)
                    )
                }
            }
        }
    }

    suspend fun addOrUpdateHttpServer(
        server: HttpMcpServerEntity,
        groupIds: Set<Long>
    ): Long {
        return db.useWriterConnection {
            it.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val id = httpMcpServerDao.insertServer(server)
                applyGroupsForEntry(
                    McpServerEntry.HttpServerEntry(server.copy(id = id)),
                    groupIds
                )
                id
            }
        }
    }

    suspend fun deleteHttpServer(server: HttpMcpServerEntity) {
        // Group associations are removed by FK cascade.
        httpMcpServerDao.deleteServer(server)
    }

    suspend fun seedDatabase() {
        val builtinMcpNames = builtinMcpRepository.getAllServlets()
            .filter { it.available }
            .map { it.name }
        db.useWriterConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                seedDefaultGroup(groupDao, builtinAssociationDao, builtinMcpNames)
            }
        }
    }
}

/**
 * Creates the default group holding every builtin, on first launch only. An existing group is left
 * alone: a builtin missing from it was switched off by the user, and re-adding it would undo that.
 * Builtins added in later versions reach existing groups through a database migration instead.
 */
internal suspend fun seedDefaultGroup(
    groupDao: McpSandboxGroupDao,
    builtinAssociationDao: BuiltinMcpGroupAssociationDao,
    builtinMcpNames: List<String>,
) {
    if (groupDao.getAll().isNotEmpty()) return
    val groupId = groupDao.insertGroup(
        McpSandboxGroupEntity(
            title = "Default MCP Sandbox",
            modelType = SandboxModelType.IndexAgent,
        )
    )
    builtinAssociationDao.insertAssociations(
        builtinMcpNames.map { BuiltinMcpGroupAssociation(groupId = groupId, builtinMcpName = it) }
    )
}

sealed class McpServerEntry {
    data class HttpServerEntry(val server: HttpMcpServerEntity) : McpServerEntry()
    data class BuiltinMcpEntry(val builtinMcpName: String) : McpServerEntry()
}