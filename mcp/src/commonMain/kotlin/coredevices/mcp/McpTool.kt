package coredevices.mcp

import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

interface McpTool {
    val definition: Tool
    /** Every call waits on the user's confirmation, which the tool asks for itself. */
    val requiresConfirmation: Boolean get() = false
    suspend fun getExtraContext(): String?
    suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}