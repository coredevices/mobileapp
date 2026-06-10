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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class McpSandboxRepository(
    private val groupDao: McpSandboxGroupDao,
    private val builtinAssociationDao: BuiltinMcpGroupAssociationDao,
    private val httpMcpServerDao: HttpMcpServerDao,
    private val httpMcpGroupAssociationDao: HttpMcpGroupAssociationDao,
    private val builtinMcpRepository: ServletRepository,
    private val db: RingDatabase
) {
    fun getAllGroupsFlow() = groupDao.getAllFlow()

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
        if (groupDao.getAllFlow().first().isEmpty()) {
            val defaultGroupId = groupDao.insertGroup(
                McpSandboxGroupEntity(
                    title = "Default MCP Sandbox"
                )
            )

            // Add all builtin MCPs to the default group
            val builtinMcps = builtinMcpRepository.getAllServlets().map { it.name }
            builtinAssociationDao.insertAssociations(
                builtinMcps.map {
                    BuiltinMcpGroupAssociation(
                        groupId = defaultGroupId,
                        builtinMcpName = it
                    )
                }
            )
        }
    }
}

sealed class McpServerEntry {
    data class HttpServerEntry(val server: HttpMcpServerEntity) : McpServerEntry()
    data class BuiltinMcpEntry(val builtinMcpName: String) : McpServerEntry()
}