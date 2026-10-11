package coredevices.indexai.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.MessageTag
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpIntegration
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IterativeAgentTest {

    private class FakeIntegration(
        /** Tools that need the user's go-ahead before running. */
        private val confirmationTools: Set<String> = emptySet(),
    ) : McpIntegration {
        override val name = "fake"
        val calls = mutableListOf<Pair<String, Map<String, JsonElement>>>()
        val contexts = mutableListOf<SessionContext>()
        var tools = emptyList<McpTool>()
        override suspend fun connect() {}
        override suspend fun close() {}
        override suspend fun listTools(): List<McpTool> = tools
        override suspend fun callTool(
            toolName: String,
            json: Map<String, JsonElement>,
            context: SessionContext
        ): ToolCallResult {
            calls += toolName to json
            contexts += context
            if (toolName in confirmationTools && !context.userConfirmed) {
                return ToolCallResult(
                    "Waiting for confirmation",
                    SemanticResult.RequiresConfirmation(name, toolName, JsonObject(json), destructive = true),
                )
            }
            return ToolCallResult("""{"success":true}""", null)
        }
        override suspend fun getExtraContext(sessionContext: SessionContext?): String? = null
    }

    /** Returns each scripted round's tool calls in order, then [finalContent] with no calls (= model stops). */
    private class ScriptedAgent(
        private val script: MutableList<List<ToolCall>>,
        private val finalContent: String? = null,
        private val roundContent: String? = null,
        initialConversation: List<ConversationMessageDocument> = emptyList(),
    ) : IterativeAgent(initialConversation) {
        override val label = "test"
        override val isCloud = true
        var inferences = 0
        val toolsSeen = mutableListOf<List<String>>()
        val historiesSeen = mutableListOf<List<ConversationMessageDocument>>()

        override suspend fun runInference(
            input: String,
            history: List<ConversationMessageDocument>,
            tools: List<McpSessionTool>,
            mcpSession: McpSession,
            sessionContext: SessionContext,
            includePromptsFromMcps: Map<String, Set<String>>,
        ): ConversationMessageDocument {
            inferences++
            toolsSeen += tools.map { it.tool.definition.name }
            historiesSeen += history
            if (script.isEmpty()) {
                return ConversationMessageDocument(role = MessageRole.assistant, content = finalContent)
            }
            return ConversationMessageDocument(
                role = MessageRole.assistant,
                content = roundContent,
                tool_calls = script.removeAt(0),
            )
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
    fun eachCallGetsItsOwnToolCallIdInSessionContext() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(
                listOf(
                    toolCall("c1", args = """{"message":"Buy milk"}"""),
                    toolCall("c2", name = "fake__create_note", args = """{"text":"note"}"""),
                ),
            )
        )
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        assertEquals(listOf("c1", "c2"), integration.contexts.map { it.toolCallId })
    }

    @Test
    fun toolsAreListedAfreshEachRound() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(listOf(toolCall("c1", args = """{"message":"Buy milk"}""")))
        )
        val session = object : McpSession(listOf(integration), this@runTest) {
            override suspend fun callTool(
                integrationName: String,
                toolName: String,
                jsonInput: Map<String, JsonElement>,
                context: SessionContext,
                requireExists: Boolean
            ): ToolCallResult {
                integration.tools = listOf(FakeTool("unlocked"))
                return super.callTool(integrationName, toolName, jsonInput, context, requireExists)
            }
        }
        agent.send("input", session, sessionContext())

        assertEquals(listOf(emptyList(), listOf("unlocked")), agent.toolsSeen)
    }

    private class FakeTool(name: String) : BuiltInMcpTool(
        definition = Tool(name = name, inputSchema = ToolSchema())
    ) {
        override suspend fun call(jsonInput: String, context: SessionContext) = ToolCallResult("ok", null)
    }

    @Test
    fun emitsOneIterationPerInferenceRound() = runTest(UnconfinedTestDispatcher()) {
        val integration = FakeIntegration().apply { tools = listOf(FakeTool("create_reminder")) }
        val agent = ScriptedAgent(
            mutableListOf(listOf(toolCall("c1", args = """{"message":"Buy milk"}"""))),
            finalContent = "Done",
        )
        val seen = mutableListOf<AgentIteration>()
        val watcher = backgroundScope.launch { agent.iterations.collect { seen += it } }

        agent.send("input", McpSession(listOf(integration), this), sessionContext())
        watcher.cancel()

        assertEquals(2, seen.size)
        assertTrue(seen.all { it.agent == "test" })
        assertEquals(
            listOf("fake" to "create_reminder"),
            seen[0].toolDeclarations.map { it.integrationName to it.definition.name },
        )
        assertEquals(listOf(MessageRole.user), seen[0].conversation.map { it.role })
        assertEquals(
            listOf(MessageRole.user, MessageRole.assistant, MessageRole.tool),
            seen[1].conversation.map { it.role },
        )
    }

    @Test
    fun queryAnswerIsStrippedAndTagged() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(listOf(toolCall("c1", args = """{"message":"Buy milk"}"""))),
            finalContent = "<query_answer>Milk is on the list.",
        )
        agent.send("is milk on the list?", McpSession(listOf(integration), this), sessionContext())

        val conversation = agent.conversation.first()
        val answer = conversation.last()
        assertEquals(MessageRole.assistant, answer.role)
        assertEquals("Milk is on the list.", answer.content)
        assertEquals(listOf(MessageTag.AnswerResponse()), answer.messageTags)
        assertEquals(1, integration.calls.size)
        assertEquals(2, agent.inferences)
        assertNoDanglingToolCalls(agent)
    }

    @Test
    fun actionCompleteIsOnlyStripped() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(mutableListOf(), finalContent = "<action_complete>Done")
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        val conversation = agent.conversation.first()
        assertEquals(listOf(MessageRole.user, MessageRole.assistant), conversation.map { it.role })
        assertEquals("Done", conversation.last().content)
        assertNull(conversation.last().messageTags)
    }

    @Test
    fun markerOnToolCallingMessageIsStrippedWithoutAnswer() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            mutableListOf(listOf(toolCall("c1", args = """{"message":"Buy milk"}"""))),
            finalContent = "<action_complete>Done",
            roundContent = "<query_answer>Adding it now",
        )
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        val assistants = agent.conversation.first().filter { it.role == MessageRole.assistant }
        assertEquals(listOf("Adding it now", "Done"), assistants.map { it.content })
        assertEquals(listOf(null, null), assistants.map { it.messageTags })
    }

    @Test
    fun unmarkedFinalMessageIsUnchanged() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(mutableListOf(), finalContent = "Done")
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        val conversation = agent.conversation.first()
        assertEquals(2, conversation.size)
        assertEquals("Done", conversation.last().content)
        assertNull(conversation.last().messageTags)
    }

    @Test
    fun distinctCallsBeyondRoundCapStillThrow() = runTest {
        val integration = FakeIntegration()
        val agent = ScriptedAgent(
            MutableList(10) { round ->
                listOf(toolCall("c$round", args = """{"message":"item $round"}"""))
            }
        )
        val e = assertFailsWith<Exception> {
            agent.send("input", McpSession(listOf(integration), this), sessionContext())
        }
        assertEquals("Exceeded maximum tool iterations", e.message)
        assertEquals(8, integration.calls.size)
    }

    @Test
    fun confirmationRequestPausesTheLoopAfterTheRoundsOtherCalls() = runTest {
        val integration = FakeIntegration(confirmationTools = setOf("delete_note"))
        val agent = ScriptedAgent(
            mutableListOf(
                listOf(
                    toolCall("c1", name = "fake__delete_note", args = """{"id":"n1"}"""),
                    toolCall("c2", name = "fake__create_note", args = """{"text":"note"}"""),
                ),
                listOf(toolCall("c3", name = "fake__create_note", args = """{"text":"never"}""")),
            )
        )
        agent.send("input", McpSession(listOf(integration), this), sessionContext())

        assertEquals(listOf("delete_note", "create_note"), integration.calls.map { it.first })
        assertEquals(1, agent.inferences)
        val conversation = agent.conversation.first()
        val placeholder = conversation.single { it.tool_call_id == "c1" }
        assertTrue(placeholder.awaitsConfirmation)
        assertEquals(listOf(placeholder), conversation.pendingConfirmations())
        assertNoDanglingToolCalls(agent)
    }

    @Test
    fun resumeContinuesTheLoopWithoutAUserMessageAndHidesPlaceholdersFromInference() = runTest {
        val integration = FakeIntegration()
        val placeholder = ConversationMessageDocument(
            role = MessageRole.tool,
            content = "Waiting for confirmation",
            tool_call_id = "c1",
            semantic_result = SemanticResult.RequiresConfirmation("fake", "delete_note", JsonObject(emptyMap()), destructive = true),
        )
        val realResult = ConversationMessageDocument(role = MessageRole.tool, content = "deleted", tool_call_id = "c1")
        val initial = listOf(
            ConversationMessageDocument(role = MessageRole.user, content = "delete note n1"),
            ConversationMessageDocument(
                role = MessageRole.assistant,
                tool_calls = listOf(toolCall("c1", name = "fake__delete_note", args = "{}")),
            ),
            placeholder,
            realResult,
        )
        val agent = ScriptedAgent(
            mutableListOf(listOf(toolCall("c2", name = "fake__create_note", args = """{"text":"done"}"""))),
            finalContent = "Done",
            initialConversation = initial,
        )
        agent.resume(McpSession(listOf(integration), this), sessionContext())

        assertEquals(2, agent.inferences)
        assertEquals(listOf("create_note"), integration.calls.map { it.first })
        assertEquals(initial.minus(placeholder), agent.historiesSeen.first())
        assertTrue(agent.historiesSeen.none { history -> history.any { it.awaitsConfirmation } })
        val conversation = agent.conversation.first()
        assertEquals(1, conversation.count { it.role == MessageRole.user })
        assertEquals("Done", conversation.last().content)
        assertTrue(conversation.pendingConfirmations().isEmpty())
        assertNoDanglingToolCalls(agent)
    }
}
