package coredevices.ring.agent.pebble_plugin

import coredevices.analytics.HEARTBEAT_COUNTER_PLUGIN_INVOCATIONS
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.RecordingCoreAnalytics
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.PluginErrors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val PLUGIN_UUID = Uuid.parse("00000000-0000-0000-0002-00000000000b")

private val SET_PLAYING_SCHEMA = Json.parseToJsonElement(
    """{"type":"object","properties":{"on":{"type":"boolean"}},"required":["on"]}"""
).jsonObject

private val setPlaying = ActionDeclaration(
    name = "set_playing",
    description = "Resume or pause playback.",
    parameters = SET_PLAYING_SCHEMA,
)

private val next = ActionDeclaration(name = "next", description = "Skip to the next track.")
private val clearQueue = ActionDeclaration(
    name = "clear_queue",
    description = "Empty the play queue.",
    destructive = true,
    requiresConfirmation = true,
)
private val createNote = ActionDeclaration(name = "create_note")

private val context = SessionContext(timeBase = null, userMessageText = CompletableDeferred(null))

private fun integrationFor(plugin: FakePlugin, analytics: RecordingCoreAnalytics = RecordingCoreAnalytics()) =
    PblPluginMcpIntegration(plugin, analytics)

class PblPluginMcpIntegrationTest {

    @Test
    fun nameAndUuidComeFromThePlugin() {
        val integration = integrationFor(FakePlugin(listOf(setPlaying)))

        assertEquals("Music", integration.name)
        assertEquals(PLUGIN_UUID, integration.pluginUuid)
    }

    @Test
    fun listsOneToolPerDeclaredAction() = runTest {
        val tools = integrationFor(FakePlugin(listOf(setPlaying, next))).listTools()

        assertEquals(listOf("set_playing", "next"), tools.map { it.definition.name })
        assertEquals("Skip to the next track.", tools[1].definition.description)
    }

    @Test
    fun destinationActionsAreNotListedAsTools() = runTest {
        val tools = integrationFor(FakePlugin(listOf(createNote, next))).listTools()

        assertEquals(listOf("next"), tools.map { it.definition.name })
    }

    @Test
    fun toolDefinitionExposesTheSchemaPropertiesAndRequiredParams() {
        val definition = setPlaying.toToolDefinition()

        assertEquals(SET_PLAYING_SCHEMA.getValue("properties").jsonObject, definition.inputSchema.properties)
        assertEquals(listOf("on"), definition.inputSchema.required)
    }

    @Test
    fun actionWithoutParametersHasAnEmptySchema() {
        val definition = next.toToolDefinition()

        assertTrue(definition.inputSchema.properties.isNullOrEmpty())
        assertTrue(definition.inputSchema.required.isNullOrEmpty())
    }

    @Test
    fun callingAToolInvokesTheActionWithItsArguments() = runTest {
        val plugin = FakePlugin(listOf(setPlaying))
        val tool = integrationFor(plugin).listTools().single()

        val result = tool.call("""{"on":true}""", context)

        assertEquals(listOf("set_playing" to JsonObject(mapOf("on" to JsonPrimitive(true)))), plugin.invoked)
        assertEquals("done", result.resultString)
        assertIsNot<SemanticResult.GenericFailure>(result.semanticResult)
    }

    @Test
    fun failedActionIsReportedAsARecoverableFailure() = runTest {
        val plugin = FakePlugin(
            actions = listOf(setPlaying),
            result = ActionResult.error(PluginErrors.INVALID_ARGS, "bad").copy(text = "Nothing is playing"),
        )

        val result = integrationFor(plugin)
            .callTool("set_playing", mapOf("on" to JsonPrimitive(true)), context)

        assertEquals("INVALID_ARGS: bad", result.resultString)
        val failure = assertIs<SemanticResult.GenericFailure>(result.semanticResult)
        assertEquals("bad", failure.userErrorMessage)
        assertTrue(failure.llmRecoverable)
    }

    @Test
    fun missingResultTextYieldsOk() = runTest {
        val plugin = FakePlugin(listOf(next), result = ActionResult(ok = true))

        val result = integrationFor(plugin).callTool("next", emptyMap(), context)

        assertEquals("OK", result.resultString)
    }

    @Test
    fun actionRequiringConfirmationIsNotInvokedUntilTheUserConfirms() = runTest {
        val plugin = FakePlugin(listOf(clearQueue))
        val integration = integrationFor(plugin)
        val args = mapOf("all" to JsonPrimitive(true))

        val result = integration.callTool("clear_queue", args, context)

        assertTrue(plugin.invoked.isEmpty())
        val request = assertIs<SemanticResult.RequiresConfirmation>(result.semanticResult)
        assertEquals("Music", request.integrationName)
        assertEquals("clear_queue", request.toolName)
        assertEquals(JsonObject(args), request.arguments)
        assertTrue(request.destructive)
        assertEquals("Empty the play queue.", request.description)

        val confirmed = integration.callTool("clear_queue", args, context.copy(userConfirmed = true))

        assertEquals(listOf("clear_queue" to JsonObject(args)), plugin.invoked)
        assertEquals("done", confirmed.resultString)
        assertIs<SemanticResult.GenericSuccess>(confirmed.semanticResult)
    }

    @Test
    fun invocationsAreCountedForTheHeartbeat() = runTest {
        val analytics = RecordingCoreAnalytics()
        val integration = integrationFor(FakePlugin(listOf(next, clearQueue)), analytics)

        integration.callTool("next", emptyMap(), context)
        integration.callTool("next", emptyMap(), context)
        integration.callTool("clear_queue", emptyMap(), context)
        integration.callTool("missing", emptyMap(), context)

        assertEquals(mapOf(HEARTBEAT_COUNTER_PLUGIN_INVOCATIONS to 2), analytics.counters)
    }
}
