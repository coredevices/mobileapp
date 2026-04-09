package coredevices.mcp.client

import coredevices.mcp.McpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool

class RemoteMcpTool internal constructor(
    private val integration: McpIntegration,
    private val toolDef: Tool,
    override val extraContext: String? = null,
): McpTool {
    override val definition: Tool
        get() = toolDef

    override suspend fun call(jsonInput: String): ToolCallResult {
        return integration.callTool(toolName = toolDef.name, json = McpJson.decodeFromString(jsonInput))
    }
}