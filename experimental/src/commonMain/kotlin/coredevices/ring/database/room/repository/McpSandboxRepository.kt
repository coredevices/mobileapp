package coredevices.ring.database.room.repository

import androidx.room.Transactor
import androidx.room.useWriterConnection
import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.entity.mcp_sandbox.BuiltinMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpGroupAssociation
import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
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

    suspend fun removeEntry(entry: McpServerEntry, groupId: Long) {
        when (entry) {
            is McpServerEntry.BuiltinMcpEntry -> {
                builtinAssociationDao.deleteAssociation(
                    BuiltinMcpGroupAssociation(
                        groupId = groupId,
                        builtinMcpName = entry.builtinMcpName
                    )
                )
            }
            is McpServerEntry.HttpServerEntry -> {
                httpMcpGroupAssociationDao.deleteAssociation(
                    HttpMcpGroupAssociation(
                        groupId = groupId,
                        httpMcpId = entry.server.id
                    )
                )
            }
        }
    }

    suspend fun addMcpEntryToGroup(
        groupId: Long,
        entry: McpServerEntry
    ) {
        when (entry) {
            is McpServerEntry.BuiltinMcpEntry -> {
                builtinAssociationDao.insertAssociation(
                    BuiltinMcpGroupAssociation(
                        groupId = groupId,
                        builtinMcpName = entry.builtinMcpName
                    )
                )
            }
            is McpServerEntry.HttpServerEntry -> {
                httpMcpGroupAssociationDao.insertAssociation(
                    HttpMcpGroupAssociation(
                        groupId = groupId,
                        httpMcpId = entry.server.id
                    )
                )
            }
        }
    }

    suspend fun addOrUpdateHttpServer(
        initialGroupId: Long,
        server: HttpMcpServerEntity
    ): Long {
        return db.useWriterConnection {
            it.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val id = httpMcpServerDao.insertServer(server)
                if (server.id != 0L) {
                    // If we're updating an existing server, no need to add a new association
                    return@withTransaction id
                }

                httpMcpGroupAssociationDao.insertAssociation(
                    HttpMcpGroupAssociation(
                        groupId = initialGroupId,
                        httpMcpId = id
                    )
                )
                return@withTransaction id
            }
        }
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