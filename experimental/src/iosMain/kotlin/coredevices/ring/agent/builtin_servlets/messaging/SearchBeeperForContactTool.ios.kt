package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.EXTRA_CONTEXT
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.INPUT_SCHEMA
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.TOOL_DESCRIPTION
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.TOOL_NAME
import io.modelcontextprotocol.kotlin.sdk.types.Tool

actual class SearchBeeperForContactTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = INPUT_SCHEMA
    ),
    extraContext = EXTRA_CONTEXT
) {
    actual override suspend fun call(jsonInput: String): ToolCallResult {
        throw NotImplementedError("iOS does not support sending instant messages yet.")
    }
}