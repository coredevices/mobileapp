package coredevices.ring.agent

import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.systemone.SystemOneAgent
import coredevices.indexai.agent.systemone.SystemOneAnswer
import coredevices.indexai.agent.systemone.SystemOneAnswers
import coredevices.indexai.agent.systemone.SystemOneException
import coredevices.indexai.agent.systemone.SystemOneQuestion
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.plugins.PluginsServlet
import coredevices.ring.agent.builtin_servlets.plugins.ReadSourceTool
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SystemOneToolCallValidatorTest {
    private class FakeSystemOneAgent(private val probability: suspend () -> Double) : SystemOneAgent {
        var state: JsonElement? = null
        var questions: List<SystemOneQuestion<*>>? = null

        override suspend fun decide(state: JsonElement, questions: List<SystemOneQuestion<*>>): SystemOneAnswers {
            this.state = state
            this.questions = questions
            return SystemOneAnswers.of(
                questions,
                mapOf(SystemOneToolCallValidator.QUESTION to SystemOneAnswer.YesNo(probability())),
            )
        }
    }

    private class FakeTool(name: String, description: String, annotations: ToolAnnotations? = null) : BuiltInMcpTool(
        definition = Tool(name = name, description = description, inputSchema = ToolSchema(), annotations = annotations)
    ) {
        override suspend fun call(jsonInput: String, context: SessionContext) = ToolCallResult("called", null)
    }

    private val arguments = mapOf<String, JsonElement>("contact_name" to JsonPrimitive("Sam"), "text" to JsonPrimitive("On my way"))
    private val call = AgentToolCall("c2", "messaging", "send_message", arguments)
    private val tool = McpSessionTool("messaging", FakeTool("send_message", "Sends a message to a contact"))
    private val conversation = listOf(
        ConversationMessageDocument(role = MessageRole.user, content = "Tell Sam I'm on my way"),
        ConversationMessageDocument(
            role = MessageRole.assistant,
            tool_calls = listOf(ToolCall("c1", "function", FunctionToolCall("contacts__find", """{"name":"Sam"}"""))),
        ),
        ConversationMessageDocument(role = MessageRole.tool, content = "Sam Lee", tool_call_id = "c1"),
        ConversationMessageDocument(
            role = MessageRole.assistant,
            tool_calls = listOf(ToolCall("c2", "function", FunctionToolCall("messaging__send_message", "{}"))),
        ),
    )

    private suspend fun validate(
        systemOne: SystemOneAgent,
        call: AgentToolCall = this.call,
        tool: McpSessionTool? = this.tool,
    ) = SystemOneToolCallValidator(systemOne).validate(call, tool, conversation)

    @Test
    fun asksAboutTheCallWithTheConversationHistoryAsState() = runTest {
        val systemOne = FakeSystemOneAgent { 0.9 }

        assertNull(validate(systemOne))

        assertEquals(
            buildJsonObject {
                putJsonArray("conversation") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "Tell Sam I'm on my way")
                    }
                    addJsonObject {
                        put("role", "assistant")
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("name", "contacts__find")
                                put("arguments", """{"name":"Sam"}""")
                            }
                        }
                    }
                    addJsonObject {
                        put("role", "tool")
                        put("content", "Sam Lee")
                    }
                    addJsonObject {
                        put("role", "assistant")
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("name", "messaging__send_message")
                                put("arguments", "{}")
                            }
                        }
                    }
                }
                putJsonObject("tool_call") {
                    put("integration", "messaging")
                    put("tool", "send_message")
                    put("description", "Sends a message to a contact")
                    putJsonObject("arguments") {
                        put("contact_name", "Sam")
                        put("text", "On my way")
                    }
                }
            },
            systemOne.state,
        )
        assertIs<SystemOneQuestion.YesNo>(systemOne.questions!!.single())
    }

    @Test
    fun onlyAConfidentNoFailsTheCallAndCarriesItsProbability() = runTest {
        assertEquals(
            SemanticResult.ValidationFailure("messaging", "send_message", JsonObject(arguments), probabilityValid = 0.1),
            validate(FakeSystemOneAgent { 0.1 }),
        )
        assertNull(validate(FakeSystemOneAgent { SystemOneToolCallValidator.MIN_VALID_PROBABILITY }))
    }

    @Test
    fun destructiveToolNeedsAMoreConfidentYes() = runTest {
        fun sendMessage(destructive: Boolean?) = McpSessionTool(
            "messaging",
            FakeTool("send_message", "Sends a message to a contact", destructive?.let { ToolAnnotations(destructiveHint = it) }),
        )
        val unsure = (SystemOneToolCallValidator.MIN_VALID_PROBABILITY + SystemOneToolCallValidator.MIN_VALID_PROBABILITY_DESTRUCTIVE) / 2

        assertEquals(unsure, validate(FakeSystemOneAgent { unsure }, tool = sendMessage(destructive = true))?.probabilityValid)
        assertNull(validate(FakeSystemOneAgent { unsure }, tool = sendMessage(destructive = false)))
        assertNull(validate(FakeSystemOneAgent { unsure }, tool = sendMessage(destructive = null)))
        assertNull(validate(FakeSystemOneAgent { unsure }, tool = null))
        assertNull(
            validate(
                FakeSystemOneAgent { SystemOneToolCallValidator.MIN_VALID_PROBABILITY_DESTRUCTIVE },
                tool = sendMessage(destructive = true),
            )
        )
    }

    @Test
    fun readOnlyToolIsNotChecked() = runTest {
        val systemOne = FakeSystemOneAgent { 0.0 }
        fun find(readOnly: Boolean) =
            McpSessionTool("messaging", FakeTool("find_contact", "Finds a contact", ToolAnnotations(readOnlyHint = readOnly)))

        assertNull(validate(systemOne, tool = find(readOnly = true)))
        assertNull(systemOne.state)
        assertEquals(0.0, validate(systemOne, tool = find(readOnly = false))?.probabilityValid)
    }

    @Test
    fun noteCapturesSourceReadsAndToolLoadingAreNotChecked() = runTest {
        val systemOne = FakeSystemOneAgent { 0.0 }
        val unchecked = listOf(
            NoteServlet.NAME to CreateNoteTool.TOOL_NAME,
            PluginsServlet.NAME to ReadSourceTool.TOOL_NAME,
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME to LazyLoadingMcpSession.LOAD_TOOLS_NAME,
            LazyLoadingMcpSession.LOADER_INTEGRATION_NAME to LazyLoadingMcpSession.SEARCH_TOOLS_NAME,
        )

        unchecked.forEach { (integration, tool) ->
            assertNull(validate(systemOne, AgentToolCall("c3", integration, tool, emptyMap())))
        }
        assertNull(systemOne.state)
    }

    @Test
    fun callPassesWhenNoVerdictCanBeHad() = runTest {
        assertNull(validate(FakeSystemOneAgent { throw SystemOneException("rejected") }))
        assertNull(validate(FakeSystemOneAgent { throw IOException("offline") }))
        assertNull(
            validate(
                FakeSystemOneAgent {
                    delay(SystemOneToolCallValidator.VALIDATE_TIMEOUT + 1.seconds)
                    0.0
                }
            )
        )
    }
}
