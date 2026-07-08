package coredevices.indexai.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpIntegration
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.ToolCallResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class IterativeAgentTest {

    private class FakeIntegration : McpIntegration {
        override val name = "fake"
        val calls = mutableListOf<Pair<String, Map<String, JsonElement>>>()
        override suspend fun resetCache() {}
        override suspend fun connect() {}
        override suspend fun close() {}
        override suspend fun listTools(): List<McpTool> = emptyList()
        override suspend fun callTool(
            toolName: String,
            json: Map<String, JsonElement>,
            context: SessionContext
        ): ToolCallResult {
            calls += toolName to json
            return ToolCallResult("""{"success":true}""", null)
        }
        override suspend fun getExtraContext(sessionContext: SessionContext?): String? = null
    }

    /** Returns each scripted round's tool calls in order, then empty (= model stops). */
    private class ScriptedAgent(
        private val script: MutableList<List<ToolCall>>,
    ) : IterativeAgent(emptyList()) {
        override val label = "test"
        var inferences = 0

        override suspend fun runInference(
            input: String,
            history: List<ConversationMessageDocument>,
            tools: List<McpSessionTool>,
            mcpSession: McpSession,
            sessionContext: SessionContext,
            includePromptsFromMcps: Map<String, Set<String>>,
        ): ConversationMessageDocument {
            inferences++
            val calls = if (script.isNotEmpty()) script.removeAt(0) else emptyList()
            return ConversationMessageDocument(role = MessageRole.assistant, tool_calls = calls)
        }

        override fun decodeToolCalls(assistantMessage: ConversationMessageDocument): List<AgentToolCall> =
            (assistantMessage.tool_calls ?: emptyList()).map { call ->
                val composite = call.function!!.name.split("__", limit = 2)
                AgentToolCall(
                    id = call.id,
                    integrationName = composite[0],
                    toolName = composite[1],
                    arguments = Json.decodeFromString(call.function!!.arguments),
                )
            }
    }

    private fun toolCall(id: String, name: String = "fake__create_reminder", args: String) =
        ToolCall(id = id, type = "function", function = FunctionToolCall(name = name, arguments = args))

    private fun sessionContext() =
        SessionContext(timeBase = null, userMessageText = CompletableDeferred("test"))

    private suspend fun assertNoDanglingToolCalls(agent: Agent) {
        val conversation = agent.conversation.first()
        val answeredIds = conversation.filter { it.role == MessageRole.tool }.map { it.tool_call_id }
        conversation.filter { it.role == MessageRole.assistant }
            .flatMap { it.tool_calls ?: emptyList() }
            .forEach { assertContains(answeredIds, it.id, "tool call ${it.id} has no tool response") }
    }

    @Test
    fun identicalRepeatIsNotReExecutedAndEndsTurn() = runTest {
        val integration = FakeIntegration()
        // Same arguments with flipped key order, as seen from looping models
        val agent = ScriptedAgent(
            mutableListOf(
                listOf(toolCall("c1", args = """{"message":"Call Macu","date_time_human":"tomorrow morning"}""")),
                listOf(toolCall("c2", args = """{"date_time_human":"tomorrow morning","message":"Call Macu"}""")),
            )
        )
        agent.send("call Macu in the morning", McpSession(listOf(integration), this), sessionContext())

        assertEquals(1, integration.calls.size)
        assertEquals(2, agent.inferences)
        val last = agent.conversation.first().last()
        assertEquals(MessageRole.tool, last.role)
        assertEquals("c2", last.tool_call_id)
        assertContains(assertNotNull(last.content), "Not executed")
        assertNoDanglingToolCalls(agent)
    }

    @Test
    fun distinctCallsAllExecute() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(
                listOf(toolCall("c1", args = """{"message":"Buy milk"}""")),
                listOf(toolCall("c2", args = """{"message":"Buy bread"}""")),
            )
        )
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        assertEquals(2, integration.calls.size)
        assertEquals(3, agent.inferences)
        assertNoDanglingToolCalls(agent)
    }

    @Test
    fun nonConsecutiveRepeatIsDetected() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(
                listOf(toolCall("c1", args = """{"message":"Call Macu"}""")),
                listOf(toolCall("c2", name = "fake__create_note", args = """{"text":"note"}""")),
                listOf(toolCall("c3", args = """{"message":"Call Macu"}""")),
            )
        )
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        assertEquals(listOf("create_reminder", "create_note"), integration.calls.map { it.first })
        assertEquals(3, agent.inferences)
        assertNoDanglingToolCalls(agent)
    }

    @Test
    fun mixedRoundSkipsRepeatAndExecutesFreshCall() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(
                listOf(toolCall("c1", args = """{"message":"Call Macu"}""")),
                listOf(
                    toolCall("c2", args = """{"message":"Call Macu"}"""),
                    toolCall("c3", name = "fake__create_note", args = """{"text":"note"}"""),
                ),
            )
        )
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        assertEquals(listOf("create_reminder", "create_note"), integration.calls.map { it.first })
        assertEquals(3, agent.inferences)
        val duplicateResponse = agent.conversation.first().first { it.tool_call_id == "c2" }
        assertContains(assertNotNull(duplicateResponse.content), "Not executed")
        assertNoDanglingToolCalls(agent)
    }

    @Test
    fun distinctCallsBeyondRoundCapStillThrow() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            MutableList(5) { round ->
                listOf(toolCall("c$round", args = """{"message":"item $round"}"""))
            }
        )
        val e = assertFailsWith<Exception> {
            agent.send("input", McpSession(listOf(integration), this), sessionContext())
        }
        assertEquals("Exceeded maximum tool iterations", e.message)
        assertEquals(3, integration.calls.size)
    }
}
