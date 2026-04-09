package coredevices.ring.agent.builtin_servlets.messaging

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

expect class SearchBeeperForContactTool() : BuiltInMcpTool {
    override suspend fun call(jsonInput: String): ToolCallResult
}

@Serializable
internal data class SearchBeeperForContactArgs(
    val name: String
)

internal object SearchBeeperForContactToolConstants {
    val INPUT_SCHEMA = ToolSchema(
        properties = JsonObject(
            mapOf(
                "name" to JsonObject(
                    mapOf(
                        "type" to "string",
                        "description" to "The name of the contact to search for."
                    ).toJson()
                ),
            )
        ),
        required = listOf("name")
    )
    val TOOL_NAME: String = "search_for_contact"
    val TOOL_DESCRIPTION: String =
        "Searches for a contact in Beeper by name to obtain their contact ID."
    val EXTRA_CONTEXT: String =
        "When using search_for_contact, refuse to continue if there are multiple contacts with the same name and instead ask the user for more specific information."
}