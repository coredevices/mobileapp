package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

expect class SendBeeperMessageTool() : BuiltInMcpTool {
    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult
}

@Serializable
internal data class SendBeeperMessageArgs(
    val contactName: String,
    val text: String
)

internal object SendBeeperMessageToolConstants {
    val INPUT_SCHEMA = ToolSchema(
        properties = JsonObject(
            mapOf(
                "contact_name" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "The name of the contact to send the instant message to."
                    ).toJson()
                ),
                "text" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "The instant message contents to be sent to the contact."
                    ).toJson()
                )
            )
        ),
        required = listOf("contact_name", "text")
    )
    val TOOL_NAME: String = "send_instant_message"
    val TOOL_DESCRIPTION: String = "Sends an instant message to a specified contact."
}