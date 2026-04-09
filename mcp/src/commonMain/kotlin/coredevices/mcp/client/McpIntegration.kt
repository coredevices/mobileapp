package coredevices.mcp.client

import coredevices.mcp.McpTool
import coredevices.mcp.data.McpPrompt
import coredevices.mcp.data.ToolCallResult
import kotlinx.serialization.json.JsonElement

interface McpIntegration {
    val name: String
    suspend fun resetCache()
    suspend fun connect()
    suspend fun close()
    suspend fun listTools(): List<McpTool>
    suspend fun callTool(toolName: String, json: Map<String, JsonElement>): ToolCallResult
    suspend fun getExtraContext(): String?
}

interface PromptProvider: McpIntegration {
    suspend fun listPrompts(): List<McpPrompt>
    suspend fun getPromptContent(promptName: String): String
    suspend fun getExtraContext(includePromptsFrom: Set<String>? = null): String?
}