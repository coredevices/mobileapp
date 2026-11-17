package coredevices.ring.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.api.DecisionQuestion
import coredevices.ring.api.DecisionResult
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaModel
import coredevices.ring.api.RunResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AgentNenyaToolSchemaTest {

    private object UnusedNenyaClient : NenyaClient {
        override suspend fun run(
            conversationHistory: List<ConversationMessageDocument>,
            toolSpecs: List<ToolDeclaration>,
            additionalContext: String,
            timezone: TimeZone,
            model: NenyaModel,
        ): RunResult = error("not called")

        override suspend fun decide(
            state: JsonElement,
            questions: Map<String, DecisionQuestion>,
            model: String?,
        ): DecisionResult = error("not called")
    }

    private class TestAgent : AgentNenya(UnusedNenyaClient, "", NenyaModel.Default, emptyList())

    private class StaticTool(override val definition: Tool) : McpTool {
        override suspend fun getExtraContext(): String? = null
        override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult = error("not called")
    }

    private fun declare(properties: String, required: List<String> = emptyList(), defs: String? = null): FunctionDeclaration {
        val schema = ToolSchema(
            properties = Json.parseToJsonElement(properties).jsonObject,
            required = required,
            defs = defs?.let { Json.parseToJsonElement(it).jsonObject },
        )
        val tool = McpSessionTool("TickTick", StaticTool(Tool(name = "create_task", inputSchema = schema)))
        return TestAgent().prepareTools(listOf(tool)).single().function!!
    }

    private val taskDefs = """{"TaskInput": {"type": "object", "title": "TaskInput", "description": "A task.",
        "properties": {
            "title": {"type": "string", "title": "Title"},
            "priority": {"anyOf": [{"type": "integer", "enum": [0, 1, 3, 5]}, {"type": "null"}], "default": null, "description": "Priority"},
            "items": {"anyOf": [{"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/ChecklistItem"}}, {"type": "null"}], "default": null}
        },
        "required": ["title"]},
        "ChecklistItem": {"type": "object", "properties": {"title": {"type": "string"}}}}"""

    @Test
    fun topLevelRefBecomesTypedObjectWithItsProperties() {
        val declared = declare("""{"task": {"${'$'}ref": "#/${'$'}defs/TaskInput", "description": "The task"}}""", listOf("task"), taskDefs)

        val task = declared.parameters.properties.getValue("task")
        assertEquals(JsonPrimitive("object"), task.type)
        assertEquals("The task", task.description)
        assertEquals(listOf("title"), task.required)
        assertEquals(setOf("title", "priority", "items"), task.properties?.keys)
        assertEquals(listOf("0", "1", "3", "5"), task.properties?.getValue("priority")?.anyOf?.first()?.enum)
        assertEquals(JsonPrimitive("null"), task.properties?.getValue("priority")?.anyOf?.last()?.type)
        assertFalse(Json.encodeToString(FunctionDeclaration.serializer(), declared).contains("\$ref"))
    }

    @Test
    fun refInsideAnyOfAndItemsIsInlined() {
        val declared = declare(
            """{"task": {"anyOf": [{"${'$'}ref": "#/${'$'}defs/TaskInput"}, {"type": "null"}], "default": null},
                "tasks": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/TaskInput"}}}""",
            defs = taskDefs,
        )

        val optional = declared.parameters.properties.getValue("task")
        assertNull(optional.type)
        assertEquals(listOf(JsonPrimitive("object"), JsonPrimitive("null")), optional.anyOf?.map { it.type })
        assertEquals(setOf("title", "priority", "items"), optional.anyOf?.first()?.properties?.keys)

        val list = declared.parameters.properties.getValue("tasks")
        assertEquals(JsonPrimitive("object"), list.items?.get("type"))
        assertEquals(setOf("title", "priority", "items"), list.items?.get("properties")?.jsonObject?.keys)
        assertFalse(Json.encodeToString(FunctionDeclaration.serializer(), declared).contains("\$ref"))
    }

    @Test
    fun recursiveAndUnknownRefsBecomeBareObjects() {
        val declared = declare(
            """{"node": {"${'$'}ref": "#/${'$'}defs/Node"}, "other": {"${'$'}ref": "#/${'$'}defs/Missing", "description": "x"}}""",
            defs = """{"Node": {"type": "object", "properties": {"child": {"${'$'}ref": "#/${'$'}defs/Node"}}}}""",
        )

        val node = declared.parameters.properties.getValue("node")
        val child = node.properties?.getValue("child")
        assertEquals(JsonPrimitive("object"), child?.type)
        assertNull(child?.properties)
        val other = declared.parameters.properties.getValue("other")
        assertEquals(JsonPrimitive("object"), other.type)
        assertEquals("x", other.description)
    }

    @Test
    fun arrayWithoutItemsDefaultsToStrings() {
        val declared = declare("""{"ids": {"type": "array"}}""")

        assertEquals(JsonObject(mapOf("type" to JsonPrimitive("string"))), declared.parameters.properties.getValue("ids").items)
    }

    @Test
    fun untypedTopLevelParameterStillDropsTheTool() {
        val schema = ToolSchema(properties = Json.parseToJsonElement("""{"x": {"description": "no type"}}""").jsonObject)
        val tool = McpSessionTool("TickTick", StaticTool(Tool(name = "t", inputSchema = schema)))

        assertEquals(emptyList(), TestAgent().prepareTools(listOf(tool)))
    }
}
