package coredevices.mcp

import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

interface McpTool {
    val definition: Tool
    /** Schema shown to the on-device model, which is fine-tuned on a fixed tool list. */
    val localDefinition: Tool get() = definition
    val extraContext: String?
    suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}