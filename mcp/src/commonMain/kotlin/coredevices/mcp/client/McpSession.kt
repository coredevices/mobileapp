package coredevices.mcp.client

import co.touchlab.kermit.Logger
import coredevices.mcp.McpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.JsonElement

class McpSession(
    private val integrations: List<McpIntegration>,
    private val scope: CoroutineScope
) {
    companion object {
        private val logger = Logger.withTag("McpSession")
    }
    private val integrationLookup: Map<String, McpIntegration> = integrations.associateBy { it.name }

    suspend fun openSession() {
        for (integration in integrations) {
            try {
                integration.resetCache()
                integration.connect()
            } catch (e: Exception) {
                // Log and continue with other integrations
                logger.e(e) { "Failed to connect to integration ${integration.name}: ${e.message}" }
            }
        }
    }

    suspend fun closeSession() {
        for (integration in integrations) {
            integration.close()
        }
    }

    suspend fun listTools(): List<McpSessionTool> {
        return integrations.map {
            scope.async {
                try {
                    it.listTools().map { tool ->
                        McpSessionTool(
                            integrationName = it.name,
                            tool = tool
                        )
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Failed to list tools for integration ${it.name}: ${e.message}" }
                    emptyList()
                }
            }
        }
            .awaitAll()
            .flatten()
    }

    suspend fun getExtraContext(includePromptsFrom: Map<String, Set<String>> = emptyMap()): String? {
        return integrations
            .map {
                scope.async {
                    val includePrompts = includePromptsFrom[it.name]
                    when (it) {
                        is PromptProvider -> it.getExtraContext(includePrompts)
                        else -> it.getExtraContext()
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Calls a tool by its integration and tool name.
     * @throws IllegalArgumentException if requireExists is true and the integration is not found.
     * @param integrationName The name of the integration.
     * @param toolName The name of the tool to call.
     * @param jsonInput The JSON input parameters for the tool.
     * @param requireExists If true, throws an exception if the integration is not found.
     * @return ToolCallResult indicating success or failure of the tool call.
     */
    suspend fun callTool(
        integrationName: String,
        toolName: String,
        jsonInput: Map<String, JsonElement>,
        requireExists: Boolean = false
    ): ToolCallResult {
        val integration = integrationLookup[integrationName]
            ?: if (requireExists) {
                throw IllegalArgumentException("Integration $integrationName not found")
            } else {
                return ToolCallResult(
                    "Unknown tool name",
                    SemanticResult.GenericFailure("Invalid tool call")
                )
            }
        return integration.callTool(toolName, jsonInput)
    }
}

data class McpSessionTool(
    val integrationName: String,
    val tool: McpTool
)