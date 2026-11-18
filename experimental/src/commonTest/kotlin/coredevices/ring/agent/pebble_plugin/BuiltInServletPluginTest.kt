package coredevices.ring.agent.pebble_plugin

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val OK = ToolCallResult("{\"success\": true}", SemanticResult.GenericSuccess)

/** Records every call and answers with [result]. */
private class RecordingTool(
    name: String,
    private val result: ToolCallResult = OK,
) : BuiltInMcpTool(
    definition = Tool(
        name = name,
        description = "Does $name",
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "text" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                    "label" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                )
            ),
            required = listOf("text"),
        ),
    ),
) {
    val calls = mutableListOf<Pair<String, SessionContext>>()

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        calls += jsonInput to context
        return result
    }
}

/** A servlet plugin over [tools], with [disabled] switched off and a fixed clock. */
private class FakeServletPlugin(
    tools: List<McpTool>,
    private val disabled: Set<String> = emptySet(),
    override val clock: Clock,
) : BuiltInServletPlugin(
    name = "builtin_fake",
    tools = tools,
    pluginUuid = Uuid.parse("00000000-0000-0000-0002-0000000000ff"),
    pluginName = "Fake",
    description = "A fake servlet",
    callerPermission = PluginPermissions.WRITE_INDEX_NOTES,
) {
    override suspend fun getDisabledTools(): List<String> = disabled.toList()
}

class BuiltInServletPluginTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private fun plugin(vararg tools: McpTool, disabled: Set<String> = emptySet()) =
        FakeServletPlugin(tools.toList(), disabled, clock)

    @Test
    fun `the servlet id stays the integration name while the plugin shows its display name`() {
        val plugin = plugin(RecordingTool("create_thing"))
        assertEquals("builtin_fake", plugin.name)
        assertEquals("Fake", plugin.metadata.name)
        assertEquals("A fake servlet", plugin.metadata.description)
        assertEquals(listOf("create_thing"), plugin.metadata.actions.map { it.name })
    }

    @Test
    fun `each tool is an action carrying the tool's schema and the caller permission`() {
        val plugin = plugin(RecordingTool("create_thing"), RecordingTool("list_things"))

        assertEquals(listOf("create_thing", "list_things"), plugin.actions.map { it.name })
        val action = plugin.action("create_thing")!!
        assertEquals("Does create_thing", action.description)
        assertEquals(listOf(PluginPermission(PluginPermissions.WRITE_INDEX_NOTES)), action.callerPermissions)
        val parameters = action.parameters!!
        assertEquals("object", parameters["type"]!!.jsonPrimitive.content)
        assertEquals(setOf("text", "label"), parameters["properties"]!!.jsonObject.keys)
        assertEquals("string", parameters["properties"]!!.jsonObject["text"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("text"), action.requiredParams)
        assertFalse(action.bindable)
    }

    @Test
    fun `declares no sources`() = runTest {
        val plugin = plugin(RecordingTool("create_thing"))
        assertEquals(emptyList(), plugin.sources)
        assertFalse(plugin.serves("notes", "note"))
        assertEquals(emptyList(), plugin.observe("notes", "note", null, null).toList())
    }

    @Test
    fun `invoking passes the arguments through as JSON with the clock as time base`() = runTest {
        val tool = RecordingTool("create_thing")
        val args = buildJsonObject {
            put("text", "milk")
            put("label", "shopping")
        }

        val result = plugin(tool).invoke("create_thing", args)

        assertTrue(result.ok)
        val (jsonInput, context) = tool.calls.single()
        assertEquals(args, Json.parseToJsonElement(jsonInput).jsonObject)
        assertEquals(now, context.timeBase)
        assertNull(context.userMessageText.await())
        assertFalse(context.userConfirmed)
    }

    @Test
    fun `a successful call answers with a toast, or the raw result when there is none`() = runTest {
        val alarm = RecordingTool("set_alarm", ToolCallResult("Alarm created", SemanticResult.AlarmCreation(LocalTime(8, 0))))
        val list = RecordingTool("create_list_item", ToolCallResult("{}", SemanticResult.ListItemCreation("milk", listUsed = "Shopping")))
        val plain = RecordingTool("do_thing", OK)
        val plugin = plugin(alarm, list, plain)

        assertEquals("Alarm set", plugin.invoke("set_alarm", JsonObject(emptyMap())).text)
        assertEquals("Added to Shopping", plugin.invoke("create_list_item", JsonObject(emptyMap())).text)
        assertEquals("{\"success\": true}", plugin.invoke("do_thing", JsonObject(emptyMap())).text)
    }

    @Test
    fun `a failed call becomes an error, recoverable ones as invalid arguments`() = runTest {
        val badArgs = RecordingTool(
            "parse",
            ToolCallResult("{}", SemanticResult.GenericFailure("Could not parse time", llmRecoverable = true)),
        )
        val broke = RecordingTool("break", ToolCallResult("Failed: boom", SemanticResult.GenericFailure(null)))
        val plugin = plugin(badArgs, broke)

        val invalid = plugin.invoke("parse", JsonObject(emptyMap()))
        assertFalse(invalid.ok)
        assertEquals(PluginErrors.INVALID_ARGS, invalid.code)
        assertEquals("Could not parse time", invalid.message)

        val unknown = plugin.invoke("break", JsonObject(emptyMap()))
        assertEquals(PluginErrors.UNKNOWN, unknown.code)
        assertEquals("Failed: boom", unknown.message)
    }

    @Test
    fun `a call that would ask the user first is refused`() = runTest {
        val tool = RecordingTool(
            "send_instant_message",
            ToolCallResult(
                "Waiting",
                SemanticResult.RequiresConfirmation(
                    integrationName = "builtin_fake",
                    toolName = "send_instant_message",
                    arguments = JsonObject(emptyMap()),
                    destructive = false,
                ),
            ),
        )

        val result = plugin(tool).invoke("send_instant_message", JsonObject(emptyMap()))

        assertFalse(result.ok)
        assertEquals(PluginErrors.PERMISSION_DENIED, result.code)
    }

    @Test
    fun `an unknown action is unavailable and reaches no tool`() = runTest {
        val tool = RecordingTool("create_thing")

        val result = plugin(tool).invoke("delete_thing", JsonObject(emptyMap()))

        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, result.code)
        assertTrue(tool.calls.isEmpty())
    }

    @Test
    fun `a tool the servlet has switched off is unavailable`() = runTest {
        val tool = RecordingTool("send_instant_message")
        val plugin = plugin(tool, disabled = setOf("send_instant_message"))

        assertEquals(listOf("send_instant_message"), plugin.actions.map { it.name })
        val result = plugin.invoke("send_instant_message", JsonObject(emptyMap()))

        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, result.code)
        assertTrue(tool.calls.isEmpty())
    }
}
