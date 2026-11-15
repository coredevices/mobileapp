package coredevices.mcp

import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

interface McpTool {
    val definition: Tool
    suspend fun getExtraContext(): String?
    suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}