package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

expect class SendBeeperMessageTool() : BuiltInMcpTool {
    override suspend fun call(jsonInput: String): ToolCallResult
}

@Serializable
internal data class SendBeeperMessageArgs(
    val contactId: String,
    val text: String
)

internal object SendBeeperMessageToolConstants {
    val INPUT_SCHEMA = ToolSchema(
        properties = JsonObject(
            mapOf(
                "contact_id" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "The unique identifier of the contact to whom the instant message will be sent."
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
        required = listOf("contact_id", "text")
    )
    val TOOL_NAME: String = "send_instant_message"
    val TOOL_DESCRIPTION: String = "Sends an instant message to a specified contact."
}