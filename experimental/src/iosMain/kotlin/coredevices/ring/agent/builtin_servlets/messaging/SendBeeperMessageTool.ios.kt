package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.toolschema.builtinToolDefinitions

actual class SendBeeperMessageTool : BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(SendBeeperMessageToolConstants.TOOL_NAME)
) {
    actual override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        throw NotImplementedError("iOS does not support sending instant messages yet.")
    }
}