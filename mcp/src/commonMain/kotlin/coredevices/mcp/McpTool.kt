package coredevices.mcp

import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

interface McpTool {
    val definition: Tool
    val extraContext: String?
    suspend fun call(jsonInput: String): ToolCallResult
}