package coredevices.ring.agent.pebble_plugin

import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.PluginCaller
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

private fun integrationFor(plugin: FakePlugin, calls: FakePlugins = FakePlugins()) =
    PblPluginMcpIntegration(calls.install(plugin), calls)

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
    fun toolRequiresConfirmationWhenItsActionDoes() = runTest {
        val tools = integrationFor(FakePlugin(listOf(next, clearQueue))).listTools()

        assertEquals(listOf(false, true), tools.map { it.requiresConfirmation })
    }

    @Test
    fun onlyADestructiveActionIsAnnotatedAsDestructive() {
        assertEquals(true, clearQueue.toToolDefinition().annotations?.destructiveHint)
        assertEquals(null, next.toToolDefinition().annotations)
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
    fun failureWithoutCodeOrMessageFallsBackToItsTextThenError() = runTest {
        val withText = FakePlugin(listOf(next), result = ActionResult(ok = false, text = "Nothing is playing"))
        val bare = FakePlugin(listOf(next), result = ActionResult(ok = false))

        assertEquals("Nothing is playing", integrationFor(withText).callTool("next", emptyMap(), context).resultString)
        assertEquals("Error", integrationFor(bare).callTool("next", emptyMap(), context).resultString)
    }

    private suspend fun semanticResultFor(result: ActionResult): SemanticResult? =
        integrationFor(FakePlugin(listOf(next), result = result))
            .callTool("next", emptyMap(), context)
            .semanticResult

    @Test
    fun successWithoutASemanticResultIsAGenericSuccess() = runTest {
        assertIs<SemanticResult.GenericSuccess>(semanticResultFor(ActionResult(ok = true)))
    }

    @Test
    fun pluginSemanticResultIsPassedThrough() = runTest {
        val result = integrationFor(
            FakePlugin(
                actions = listOf(next),
                result = ActionResult(
                    ok = true,
                    text = "Sent",
                    semanticResult = """{"type":"MessageSent","recipientName":"Sam","text":"On my way"}""",
                ),
            )
        ).callTool("next", emptyMap(), context)

        assertEquals("Sent", result.resultString)
        assertEquals(
            SemanticResult.MessageSent(recipientName = "Sam", text = "On my way"),
            result.semanticResult,
        )
    }

    @Test
    fun pluginSemanticResultTypeMayBeCapsSnakeCase() = runTest {
        val messageSent = semanticResultFor(
            ActionResult(ok = true, semanticResult = """{"type":"MESSAGE_SENT","recipientName":"Sam"}""")
        )
        val supportingData = semanticResultFor(
            ActionResult(ok = true, semanticResult = """{"summary":"3 tracks","type":"SUPPORTING_DATA"}""")
        )

        assertEquals(SemanticResult.MessageSent(recipientName = "Sam"), messageSent)
        assertEquals(SemanticResult.SupportingData(summary = "3 tracks"), supportingData)
    }

    @Test
    fun unknownKeysInAPluginSemanticResultAreIgnored() = runTest {
        val semanticResult = semanticResultFor(
            ActionResult(
                ok = true,
                semanticResult = """{"type":"SupportingData","summary":"3 tracks","extra":1}""",
            )
        )

        assertEquals(SemanticResult.SupportingData(summary = "3 tracks"), semanticResult)
    }

    @Test
    fun disallowedPluginSemanticResultFallsBackToGenericSuccess() = runTest {
        val disallowed = listOf(
            """{"type":"UserDenied"}""",
            """{"type":"USER_DENIED"}""",
            """{"type":"RequiresConfirmation","integrationName":"Music","toolName":"next","arguments":{},"destructive":false}""",
            """{"type":"ValidationFailure","integrationName":"Music","toolName":"next","arguments":{},"probabilityValid":0.1}""",
        )

        disallowed.forEach {
            assertIs<SemanticResult.GenericSuccess>(
                semanticResultFor(ActionResult(ok = true, semanticResult = it)),
                it,
            )
        }
    }

    @Test
    fun undecodablePluginSemanticResultFallsBackToGenericSuccess() = runTest {
        val undecodable = listOf(
            "not json",
            """{"type":"NoSuchResult"}""",
            """{"type":"NO_SUCH_RESULT"}""",
            """{"type":"message_sent","recipientName":"Sam"}""",
            """{"type":7}""",
            """[]""",
            """{"type":"MessageSent"}""",
            """{"recipientName":"Sam"}""",
        )

        undecodable.forEach {
            assertIs<SemanticResult.GenericSuccess>(
                semanticResultFor(ActionResult(ok = true, semanticResult = it)),
                it,
            )
        }
    }

    @Test
    fun failedActionIgnoresThePluginSemanticResult() = runTest {
        val semanticResult = semanticResultFor(
            ActionResult.error(PluginErrors.INVALID_ARGS, "bad")
                .copy(semanticResult = """{"type":"GenericSuccess"}""")
        )

        val failure = assertIs<SemanticResult.GenericFailure>(semanticResult)
        assertEquals("bad", failure.userErrorMessage)
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
    fun invokesAsTheHostSoItMayPrompt() = runTest {
        val calls = FakePlugins()
        integrationFor(FakePlugin(listOf(next)), calls).callTool("next", emptyMap(), context)

        assertEquals(listOf<PluginCaller>(PluginCaller.Host()), calls.callers)
    }
}
