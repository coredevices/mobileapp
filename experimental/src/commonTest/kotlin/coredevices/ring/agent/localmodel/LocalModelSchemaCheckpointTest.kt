package coredevices.ring.agent.localmodel

import coredevices.ring.agent.toolschema.localModelSchemaCheckpoint
import coredevices.toolschema.LocalModelTool
import coredevices.toolschema.LocalModelToolParameter
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.cactusTools
import coredevices.ring.agent.toToolDefinition
import coredevices.util.CommonBuildKonfig
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalModelSchemaCheckpointTest {
    private class StaticTool(override val definition: Tool) : McpTool {
        override suspend fun getExtraContext(): String? = null
        override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult = error("not called")
    }

    private fun sessionTool(servlet: String, name: String) = McpSessionTool(
        servlet,
        StaticTool(Tool(name = name, description = "live description", inputSchema = ToolSchema())),
    )

    private val checkpoint = listOf(
        LocalModelTool(
            servlet = "builtin_note",
            name = "create_note",
            description = "frozen description",
            parameters = listOf(
                LocalModelToolParameter("text", "string", "the text", required = true),
                LocalModelToolParameter("tag", "string", null, required = false),
            ),
        ),
        LocalModelTool("builtin_clock", "set_timer", "frozen timer", emptyList()),
    )

    @Test
    fun checkpointIsForTheCurrentModel() {
        assertEquals(
            CommonBuildKonfig.CACTUS_LM_WEIGHTS_VERSION,
            localModelSchemaCheckpoint.modelVersion,
            "No checkpoint was generated for the current model version",
        )
    }

    @Test
    fun onlyCheckpointedToolsInTheSessionAreOffered() {
        val prepared = cactusTools(
            checkpoint,
            listOf(
                sessionTool("builtin_note", "create_note"),
                sessionTool("builtin_note", "delete_note"),
                sessionTool("Weather Face", "set_timer"),
            ),
        )

        assertEquals(mapOf("create_note" to "builtin_note"), prepared.parentMap)
    }

    @Test
    fun frozenSchemaReplacesTheLiveDefinition() {
        val prepared = cactusTools(checkpoint, listOf(sessionTool("builtin_note", "create_note")))

        assertEquals(
            Json.parseToJsonElement(
                """
                [{"type": "function", "function": {
                    "name": "create_note",
                    "description": "frozen description",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "text": {"type": "string", "description": "the text"},
                            "tag": {"type": "string"}
                        },
                        "required": ["text"]
                    }
                }}]
                """
            ),
            Json.parseToJsonElement(prepared.toolsJson),
        )
    }

    @Test
    fun recordedDeclarationCarriesTheFrozenSchema() {
        val definition = checkpoint.first().toToolDefinition()

        assertEquals("frozen description", definition.description)
        assertEquals(setOf("text", "tag"), definition.inputSchema.properties?.keys)
        assertEquals(listOf("text"), definition.inputSchema.required)
    }
}
