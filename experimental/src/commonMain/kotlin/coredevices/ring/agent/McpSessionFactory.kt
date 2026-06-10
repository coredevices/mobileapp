package coredevices.ring.agent

import coredevices.indexai.data.entity.mcp_sandbox.HttpMcpServerEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
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
    /**
     * Creates a hardcoded MCP session of built-ins supported by the Needle Agent, which is
     * fine-tuned with a set of supported tools and doesn't support dynamic integrations.
     */
    private fun createForNeedleAgent(scope: CoroutineScope): McpSession {
        val mcpIntegrations = builtinServletRepository.getAllServlets().map {
            builtinServletRepository.resolveName(it.name)!!
        }
        return McpSession(
            mcpIntegrations,
            scope
        )
    }

    suspend fun createForSandboxGroup(groupId: Long, scope: CoroutineScope): McpSession {
        val group = mcpSandboxRepository.getGroupById(groupId) ?: throw IllegalArgumentException("MCP Sandbox group with id $groupId not found")
        if (group.modelType == SandboxModelType.IndexAgent) {
            return createForNeedleAgent(scope)
        } else {
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