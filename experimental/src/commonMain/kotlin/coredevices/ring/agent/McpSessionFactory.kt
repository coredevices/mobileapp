package coredevices.ring.agent

import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.client.McpServerCache
import coredevices.mcp.client.McpSession
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.McpServerEntry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

private val implementation = Implementation(
    name = "CoreApp",
    version = "0.0.1"
)

class McpSessionFactory(
    private val mcpSandboxRepository: McpSandboxRepository,
    private val builtinServletRepository: BuiltinServletRepository,
    private val mcpServerCache: McpServerCache,
) {
    /**
     * Every model type serves exactly what its sandbox group contains, built-ins and HTTP alike.
     * Only the generic sandbox agents get lazy tool loading; built-in servlets are always loaded.
     */
    suspend fun createForSandboxGroup(groupId: Long, scope: CoroutineScope): McpSession {
        val group = mcpSandboxRepository.getGroupById(groupId)
            ?: throw IllegalArgumentException("MCP Sandbox group with id $groupId not found")
        val entries = mcpSandboxRepository.getMcpServerEntriesForGroup(groupId).first()
        val integrations = entries.mapNotNull {
            when (it) {
                is McpServerEntry.BuiltinMcpEntry -> builtinServletRepository.resolveName(it.builtinMcpName)
                is McpServerEntry.HttpServerEntry -> it.server.toMcpIntegration(mcpServerCache)
            }
        }
        if (group.modelType == SandboxModelType.IndexAgent) return McpSession(integrations, scope)
        val builtinNames = entries.filterIsInstance<McpServerEntry.BuiltinMcpEntry>().map { it.builtinMcpName }.toSet()
        return LazyLoadingMcpSession(integrations, scope, eagerIntegrations = builtinNames)
    }
}

private fun HttpMcpServerEntity.toMcpIntegration(cache: McpServerCache): HttpMcpIntegration {
    return HttpMcpIntegration(
        name = this.name,
        implementation = implementation,
        url = this.url,
        protocol = if (this.streamable) HttpMcpProtocol.Streaming else HttpMcpProtocol.Sse,
        authHeader = this.authHeader,
        cache = cache,
    )
}