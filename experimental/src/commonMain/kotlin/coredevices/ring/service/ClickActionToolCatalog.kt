package coredevices.ring.service

import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.room.repository.McpSandboxRepository
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** A tool the user can bind a click gesture to, with the schema its arguments must satisfy. */
data class CatalogTool(
    val integrationName: String,
    val definition: Tool,
) {
    val toolName: String get() = definition.name
}

/**
 * Lists every tool a click binding can call — built-in servlets and HTTP MCP servers alike,
 * from the default sandbox group. Listing HTTP servers is a network round trip, so callers
 * should treat this as a loading operation.
 */
class ClickActionToolCatalog(
    private val mcpSessionFactory: McpSessionFactory,
    private val mcpSandboxRepository: McpSandboxRepository,
) {
    companion object {
        private val SESSION_CLOSE_TIMEOUT = 3.seconds

        /** Nothing downstream of an MCP server bounds its own connect/list, so bound it here. */
        private val LIST_TIMEOUT = 20.seconds
    }

    suspend fun availableTools(): List<CatalogTool> {
        val groupId = mcpSandboxRepository.getDefaultGroupIdOrNull()
            ?: error("No MCP sandbox group configured")
        return withTimeoutOrNull(LIST_TIMEOUT) { loadTools(groupId) }
            ?: error("Timed out after $LIST_TIMEOUT waiting for MCP servers")
    }

    private suspend fun loadTools(groupId: Long): List<CatalogTool> = coroutineScope {
        val session = mcpSessionFactory.createForDirectToolCall(groupId, this)
        session.openSession()
        try {
            session.listTools()
                .map { CatalogTool(it.integrationName, it.tool.definition) }
                .sortedWith(compareBy({ it.integrationName }, { it.toolName }))
        } finally {
            // NonCancellable so the timeout above still releases the connections.
            withContext(NonCancellable) {
                withTimeout(SESSION_CLOSE_TIMEOUT) { session.closeSession() }
            }
        }
    }
}
