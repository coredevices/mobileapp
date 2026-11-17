package coredevices.ring.agent

import coredevices.indexai.agent.systemone.SystemOneAgent
import coredevices.indexai.agent.systemone.SystemOneAnswer
import coredevices.indexai.agent.systemone.SystemOneAnswers
import coredevices.indexai.agent.systemone.SystemOneQuestion
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.mcp.client.McpIntegration
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class RecordingIntentClassifierTest {
    private class FakeSystemOneAgent(choice: String, confidence: Double = 0.9) : SystemOneAgent {
        private val answer = SystemOneAnswer.Choice(choice, confidence, mapOf(choice to confidence))
        var state: JsonElement? = null
        var questions: List<SystemOneQuestion<*>>? = null

        override suspend fun decide(state: JsonElement, questions: List<SystemOneQuestion<*>>): SystemOneAnswers {
            this.state = state
            this.questions = questions
            return SystemOneAnswers.of(questions, mapOf(RecordingIntentClassifier.QUESTION to answer))
        }
    }

    private class FakeTool(name: String, description: String) : BuiltInMcpTool(
        definition = Tool(name = name, description = description, inputSchema = ToolSchema())
    ) {
        override suspend fun call(jsonInput: String, context: SessionContext) = ToolCallResult("called", null)
    }

    private fun TestScope.session(vararg integrations: McpIntegration) = McpSession(integrations.toList(), this)

    private suspend fun TestScope.classify(choice: String, confidence: Double = 0.9) =
        RecordingIntentClassifier(FakeSystemOneAgent(choice, confidence)).classify("What's on tomorrow?", session(), emptyList())

    @Test
    fun asksAboutTheTranscriptWithTheSessionToolsAsContext() = runTest {
        val systemOne = FakeSystemOneAgent("tool_question")
        val calendar = BuiltInMcpIntegration("calendar", listOf(FakeTool("list_events", "Lists calendar events")))

        val intent = RecordingIntentClassifier(systemOne).classify("What's on tomorrow?", session(calendar), emptyList())

        assertEquals(RecordingIntent.ToolQuestion, intent)
        assertEquals(
            buildJsonObject {
                put("user_recording", "What's on tomorrow?")
                putJsonArray("available_tools") {
                    addJsonObject {
                        put("name", "calendar")
                        put("tool_count", 1)
                        putJsonArray("sample_tools") {
                            addJsonObject {
                                put("name", "list_events")
                                put("description", "Lists calendar events")
                            }
                        }
                    }
                }
                putJsonArray("available_data_sources") {}
            },
            systemOne.state,
        )
        val question = assertIs<SystemOneQuestion.Choice>(systemOne.questions!!.single())
        assertEquals(listOf("note", "tool_question", "search_question", "other"), question.options.keys.toList())
    }

    @Test
    fun sessionWithoutToolsHasAnEmptyToolList() = runTest {
        val systemOne = FakeSystemOneAgent("search_question")

        RecordingIntentClassifier(systemOne).classify("Who won the game last night?", session(), emptyList())

        assertEquals(
            buildJsonObject {
                put("user_recording", "Who won the game last night?")
                putJsonArray("available_tools") {}
                putJsonArray("available_data_sources") {}
            },
            systemOne.state,
        )
    }

    @Test
    fun mapsEachChoiceToItsIntent() = runTest {
        assertEquals(RecordingIntent.Note, classify("note"))
        assertEquals(RecordingIntent.ToolQuestion, classify("tool_question"))
        assertEquals(RecordingIntent.SearchQuestion, classify("search_question"))
        assertEquals(RecordingIntent.Other, classify("other"))
    }

    @Test
    fun unsureChoiceIsNoteAndUnknownChoiceIsOther() = runTest {
        assertEquals(RecordingIntent.Note, classify("search_question", confidence = 0.4))
        assertEquals(RecordingIntent.Other, classify("reminder"))
    }

    @Test
    fun stateListsPluginDataSources() = runTest {
        val systemOne = FakeSystemOneAgent("tool_question")
        val weather = PluginMetadata(
            pluginUuid = Uuid.random(),
            name = "Weather",
            description = "Forecasts for saved locations",
            sources = listOf(SourceDeclaration(category = "weather", items = listOf("location", "alerts"))),
            actions = emptyList(),
        )

        RecordingIntentClassifier(systemOne).classify("Will it rain today?", session(), listOf(weather))

        assertEquals(
            buildJsonObject {
                put("user_recording", "Will it rain today?")
                putJsonArray("available_tools") {}
                putJsonArray("available_data_sources") {
                    addJsonObject {
                        put("plugin", "Weather")
                        put("plugin_description", "Forecasts for saved locations")
                        put("category", "weather")
                        putJsonArray("items") {
                            add("location")
                            add("alerts")
                        }
                    }
                }
            },
            systemOne.state,
        )
    }
}
