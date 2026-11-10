package coredevices.ring.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.client.McpSessionTool
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginMcpIntegration
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaModel
import coredevices.ring.api.RunResult
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentNenyaToolNamesTest {

    private object UnusedNenyaClient : NenyaClient {
        override suspend fun run(
            conversationHistory: List<ConversationMessageDocument>,
            toolSpecs: List<ToolDeclaration>,
            additionalContext: String,
            timezone: TimeZone,
            model: NenyaModel,
        ): RunResult = error("not called")
    }

    private class TestAgent : AgentNenya(UnusedNenyaClient, "", NenyaModel.Default, emptyList()) {
        fun decode(message: ConversationMessageDocument) = decodeToolCalls(message)
    }

    private fun assistantCall(name: String) = ConversationMessageDocument(
        role = MessageRole.assistant,
        tool_calls = listOf(ToolCall(id = "call-1", type = "function", function = FunctionToolCall(name, "{}"))),
    )

    private suspend fun pluginTools(pluginName: String): List<McpSessionTool> {
        val integration = PblPluginMcpIntegration(
            FakePlugin(actions = listOf(ActionDeclaration("get_forecast")), name = pluginName).metadata,
            FakePlugins(),
        )
        return integration.listTools().map { McpSessionTool(integration.name, it) }
    }

    @Test
    fun spacedIntegrationNameIsDeclaredSanitizedAndDecodedBackToTheOriginal() = runTest {
        val agent = TestAgent()

        val declared = agent.prepareTools(pluginTools("Weather Face"))
        assertEquals("Weather_Face__get_forecast", declared.single().function?.name)

        val call = agent.decode(assistantCall("Weather_Face__get_forecast")).single()
        assertEquals("Weather Face", call.integrationName)
        assertEquals("get_forecast", call.toolName)
    }

    @Test
    fun undeclaredCompositeNameStillSplitsOnTheSeparator() {
        val call = TestAgent().decode(assistantCall("builtin_note__create_note")).single()

        assertEquals("builtin_note", call.integrationName)
        assertEquals("create_note", call.toolName)
    }

    @Test
    fun nameWithoutSeparatorIsRejected() {
        assertFailsWith<Exception> { TestAgent().decode(assistantCall("get_forecast")) }
    }
}
