package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

actual class SendBeeperMessageTool : BuiltInMcpTool(
    definition = Tool(
        name = SendBeeperMessageToolConstants.TOOL_NAME,
        description = SendBeeperMessageToolConstants.TOOL_DESCRIPTION,
        inputSchema = SendBeeperMessageToolConstants.INPUT_SCHEMA
    )
) {
    actual override suspend fun call(jsonInput: String): ToolCallResult {
        throw NotImplementedError("iOS does not support sending instant messages yet.")
    }
}