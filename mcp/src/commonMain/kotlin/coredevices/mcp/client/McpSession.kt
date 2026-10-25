package coredevices.mcp.client

import co.touchlab.kermit.Logger
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.JsonElement

open class McpSession(
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

    open suspend fun listTools(): List<McpSessionTool> {
        return integrations.map {
            scope.async {
                try {
                    it.listTools().map { tool ->
                        McpSessionTool(
                            integrationName = it.name,
                            tool = tool,
                            integrationDescription = it.description,
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

    open suspend fun getExtraContext(context: SessionContext?, includePromptsFrom: Map<String, Set<String>> = emptyMap()): String? {
        return integrations
            .map {
                scope.async {
                    val includePrompts = includePromptsFrom[it.name]
                    when (it) {
                        is PromptProvider -> it.getExtraContext(context, includePrompts)
                        else -> it.getExtraContext(context)
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
     * @param context The session context shared with the tool.
     * @param requireExists If true, throws an exception if the integration is not found.
     * @return ToolCallResult indicating success or failure of the tool call.
     */
    open suspend fun callTool(
        integrationName: String,
        toolName: String,
        jsonInput: Map<String, JsonElement>,
        context: SessionContext,
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
        return integration.callTool(toolName, jsonInput, context)
    }
}

data class McpSessionTool(
    val integrationName: String,
    val tool: McpTool,
    val integrationDescription: String? = null,
)

/** The name a cloud model calls this tool by: integration and tool joined, then [sanitizeToolName]d. */
fun McpSessionTool.compositeName(): String = sanitizeToolName("${integrationName}__${tool.definition.name}")

/**
 * The strictest subset providers accept for a function name: the MCP spec's (a-z, A-Z, 0-9, _, -)
 * without dots, at most 64 characters. Covers Gemini and Claude.
 */
fun sanitizeToolName(name: String): String = name.trim().replace(TOOL_NAME_INVALID_CHARS, "_").take(MAX_TOOL_NAME_LENGTH)

private val TOOL_NAME_INVALID_CHARS = Regex("[^a-zA-Z0-9_-]")
private const val MAX_TOOL_NAME_LENGTH = 64

/**
 * Integration names must survive round-trip through the model as part of a composite tool name.
 * Validate it won't get changed by the tool-name sanitizer, doesn't use the "__" separator, and
 * leaves the tool half room inside the sanitizer's 64 character limit.
 */
fun isValidIntegrationName(name: String): Boolean =
    name.length <= MAX_INTEGRATION_NAME_LENGTH &&
        INTEGRATION_NAME_REGEX.matches(name) &&
        !name.contains("__")

private const val MAX_INTEGRATION_NAME_LENGTH = 32

private val INTEGRATION_NAME_REGEX = Regex("[A-Za-z0-9_-]+")
