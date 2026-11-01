package coredevices.ring.database.room.repository

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.entity.mcp.DisabledBuiltinMcp
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.indexai.database.dao.DisabledBuiltinMcpDao
import coredevices.indexai.database.dao.HttpMcpServerDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The MCP servers the agent can use: every built-in plus the user's HTTP servers, each on or off. */
class McpServerRepository(
    private val disabledBuiltinDao: DisabledBuiltinMcpDao,
    private val httpMcpServerDao: HttpMcpServerDao,
    private val builtinMcpRepository: ServletRepository,
) {
    fun disabledBuiltinsFlow(): Flow<Set<String>> =
        disabledBuiltinDao.getAllFlow().map { rows -> rows.mapTo(mutableSetOf()) { it.builtinMcpName } }

    fun httpServersFlow(): Flow<List<HttpMcpServerEntity>> = httpMcpServerDao.getAllFlow()

    /** The servers a session should serve: available built-ins not switched off, enabled HTTP servers. */
    fun enabledServerEntriesFlow(): Flow<List<McpServerEntry>> =
        combine(disabledBuiltinsFlow(), httpServersFlow()) { disabled, servers ->
            builtinMcpRepository.getAllServlets()
                .filter { it.available && it.name !in disabled }
                .map { McpServerEntry.BuiltinMcpEntry(it.name) } +
                servers.filter { it.enabled }.map { McpServerEntry.HttpServerEntry(it) }
        }

    suspend fun setBuiltinEnabled(builtinName: String, enabled: Boolean) {
        val row = DisabledBuiltinMcp(builtinName)
        if (enabled) disabledBuiltinDao.delete(row) else disabledBuiltinDao.insert(row)
    }

    suspend fun setHttpServerEnabled(server: HttpMcpServerEntity, enabled: Boolean) {
        httpMcpServerDao.setEnabled(server.id, enabled)
    }

    suspend fun addOrUpdateHttpServer(server: HttpMcpServerEntity): Long =
        httpMcpServerDao.insertServer(server)

    suspend fun deleteHttpServer(server: HttpMcpServerEntity) {
        httpMcpServerDao.deleteServer(server)
    }
}

sealed class McpServerEntry {
    data class HttpServerEntry(val server: HttpMcpServerEntity) : McpServerEntry()
    data class BuiltinMcpEntry(val builtinMcpName: String) : McpServerEntry()
}
