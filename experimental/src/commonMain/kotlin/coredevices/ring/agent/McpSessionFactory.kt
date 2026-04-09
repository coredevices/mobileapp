package coredevices.ring.agent

import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
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
    private val builtinServletRepository: BuiltinServletRepository
) {
    suspend fun createForSandboxGroup(groupId: Long, scope: CoroutineScope): McpSession {
        val integrations =
            mcpSandboxRepository.getMcpServerEntriesForGroup(groupId).first().mapNotNull {
                when (it) {
                    is McpServerEntry.BuiltinMcpEntry -> builtinServletRepository.resolveName(it.builtinMcpName)
                    is McpServerEntry.HttpServerEntry -> it.server.toMcpIntegration()
                }
            }
        return McpSession(integrations, scope)
    }
}

private fun HttpMcpServerEntity.toMcpIntegration(): HttpMcpIntegration {
    return HttpMcpIntegration(
        name = this.name,
        implementation = implementation,
        url = this.url,
        protocol = if (this.streamable) HttpMcpProtocol.Streaming else HttpMcpProtocol.Sse,
        authHeader = this.authHeader
    )
}