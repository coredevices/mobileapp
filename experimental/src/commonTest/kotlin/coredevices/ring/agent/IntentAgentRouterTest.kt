package coredevices.ring.agent

import coredevices.indexai.agent.Agent
import coredevices.indexai.agent.systemone.SystemOneAgent
import coredevices.indexai.agent.systemone.SystemOneAnswer
import coredevices.indexai.agent.systemone.SystemOneAnswers
import coredevices.indexai.agent.systemone.SystemOneException
import coredevices.indexai.agent.systemone.SystemOneQuestion
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class IntentAgentRouterTest {
    private class FakeAgent(override val isCloud: Boolean) : Agent {
        override val label = "fake"
        override val conversation: SharedFlow<List<ConversationMessageDocument>> = MutableSharedFlow()

        override suspend fun send(
            input: String,
            mcpSession: McpSession,
            sessionContext: SessionContext,
            includePromptsFromMcps: Map<String, Set<String>>,
            skipToolExecution: Boolean,
        ) = Unit

        override suspend fun addMessage(message: ConversationMessageDocument) = Unit
    }

    private class FakeSystemOneAgent(private val answer: suspend () -> String) : SystemOneAgent {
        var calls = 0

        override suspend fun decide(state: JsonElement, questions: List<SystemOneQuestion<*>>): SystemOneAnswers {
            calls++
            val choice = answer()
            return SystemOneAnswers.of(
                questions,
                mapOf(RecordingIntentClassifier.QUESTION to SystemOneAnswer.Choice(choice, 0.9, mapOf(choice to 0.9))),
            )
        }
    }

    private val search = FakeAgent(isCloud = true)
    private val cloud = FakeAgent(isCloud = true)

    private suspend fun TestScope.route(
        default: Agent,
        systemOne: SystemOneAgent,
        canCapture: Boolean = true,
    ): Agent = IntentAgentRouter(RecordingIntentClassifier(systemOne), { emptyList() }) { search }
        .route(default, "Who won the game last night?", McpSession(emptyList(), this), canCapture)

    @Test
    fun searchQuestionGoesToTheSearchAgent() = runTest {
        assertSame(search, route(cloud, FakeSystemOneAgent { "search_question" }))
    }

    @Test
    fun everyOtherIntentStaysOnTheDefaultAgent() = runTest {
        listOf("tool_question", "other").forEach {
            assertSame(cloud, route(cloud, FakeSystemOneAgent { it }))
        }
    }

    @Test
    fun noteSkipsTheAgentWhenTheCallerCanCaptureIt() = runTest {
        assertIs<CaptureOnlyAgent>(route(cloud, FakeSystemOneAgent { "note" }))
        assertSame(cloud, route(cloud, FakeSystemOneAgent { "note" }, canCapture = false))
    }

    @Test
    fun captureOnlyAgentOnlyRecordsTheInput() = runTest {
        val agent = CaptureOnlyAgent()

        agent.send(
            "Gate code is 1234",
            McpSession(emptyList(), this),
            SessionContext(timeBase = null, userMessageText = CompletableDeferred(null)),
        )

        assertEquals(
            listOf(ConversationMessageDocument(role = MessageRole.user, content = "Gate code is 1234")),
            agent.conversation.first(),
        )
    }

    @Test
    fun onDeviceAgentIsNotClassified() = runTest {
        val local = FakeAgent(isCloud = false)
        val systemOne = FakeSystemOneAgent { "search_question" }

        assertSame(local, route(local, systemOne))
        assertEquals(0, systemOne.calls)
    }

    @Test
    fun slowClassificationStaysOnTheDefaultAgent() = runTest {
        val stalled = FakeSystemOneAgent {
            delay(IntentAgentRouter.CLASSIFY_TIMEOUT + 1.seconds)
            "search_question"
        }

        assertSame(cloud, route(cloud, stalled))
    }

    @Test
    fun failedClassificationStaysOnTheDefaultAgent() = runTest {
        assertSame(cloud, route(cloud, FakeSystemOneAgent { throw SystemOneException("rejected") }))
        assertSame(cloud, route(cloud, FakeSystemOneAgent { throw IOException("offline") }))
    }
}
