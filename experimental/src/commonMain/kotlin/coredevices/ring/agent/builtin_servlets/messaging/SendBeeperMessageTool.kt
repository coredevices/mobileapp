package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.ToolCallResult
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import kotlinx.serialization.Serializable

expect class SendBeeperMessageTool() : BuiltInMcpTool {
    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}

@ToolDefinition(servlet = MESSAGING_SERVLET_NAME, name = SendBeeperMessageToolConstants.TOOL_NAME, description = SendBeeperMessageToolConstants.TOOL_DESCRIPTION)
@Serializable
internal data class SendBeeperMessageArgs(
    @ToolParam("The name of the contact to send the instant message to.")
    val contactName: String,
    @ToolParam("The instant message contents to be sent.")
    val text: String,
    /** Set only on the confirmed re-dispatch of a message to a non-approved contact. */
    val roomId: String? = null,
)

internal object SendBeeperMessageToolConstants {
    const val TOOL_NAME = "send_instant_message"
    const val TOOL_DESCRIPTION = "Sends an instant message to a specified contact. Use when the user asks you to e.g. 'text', 'message', or 'send a message to' someone."
}
