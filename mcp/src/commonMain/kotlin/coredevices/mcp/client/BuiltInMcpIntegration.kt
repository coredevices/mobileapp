package coredevices.mcp.client

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.McpPrompt
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.JsonElement

open class BuiltInMcpIntegration(override val name: String, protected val tools: List<McpTool>) : McpIntegration {
    private val toolMap: Map<String, McpTool> = tools.associateBy { it.definition.name }
    override suspend fun resetCache() {
        // no-op
    }

    override suspend fun connect() {
        // no-op
    }

    override suspend fun close() {
        // no-op
    }

    override suspend fun listTools(): List<McpTool> {
        val disabled = getDisabledTools()
        return tools.filterNot { it.definition.name in disabled }
    }

    override suspend fun callTool(
        toolName: String,
        json: Map<String, JsonElement>,
        context: SessionContext
    ): ToolCallResult {
        val tool = toolMap[toolName]!!
        return tool.call(jsonInput = McpJson.encodeToString(json), context = context)
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val disabled = getDisabledTools().toSet()
        return tools.filterNot { it.definition.name in disabled }.joinToString("\n") { it.extraContext ?: "" }.takeIf { it.isNotEmpty() }
    }

    protected open suspend fun getDisabledTools() = emptyList<String>()

}