package coredevices.ring.agent

import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FallbackAgentTest {

    private fun message(role: MessageRole, content: String) =
        ConversationMessageDocument(role = role, content = content)

    private class FakeAgent(
        override val label: String,
        initial: List<ConversationMessageDocument> = emptyList(),
        private val onSend: suspend FakeAgent.() -> Unit,
    ) : Agent {
        private val _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(
            replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        ).apply { tryEmit(initial) }
        override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation

        var sendCount = 0
            private set

        override suspend fun send(
            input: String,
            mcpSession: McpSession,
            sessionContext: SessionContext,
            includePromptsFromMcps: Map<String, Set<String>>,
            skipToolExecution: Boolean,
        ) {
            sendCount++
            onSend()
        }

        override suspend fun addMessage(message: ConversationMessageDocument) {
            _conversation.emit(_conversation.first() + message)
        }
    }

    private suspend fun FallbackAgent.sendTestInput(scope: CoroutineScope) = send(
        input = "remember the milk",
        mcpSession = McpSession(emptyList(), scope),
        sessionContext = SessionContext(timeBase = null, userMessageText = CompletableDeferred("remember the milk")),
    )

    @Test
    fun usesPrimaryWhenItSucceeds() = runTest {
        val primary = FakeAgent("Nenya") {
            addMessage(message(MessageRole.assistant, "noted"))
        }
        val fallback = FakeAgent("Cactus") { }
        val agent = FallbackAgent(primary, fallback, emptyList())

        agent.sendTestInput(backgroundScope)

        assertEquals(1, primary.sendCount)
        assertEquals(0, fallback.sendCount)
        assertEquals(listOf("noted"), agent.conversation.first().map { it.content })
        assertEquals("Nenya", agent.label)
    }

    @Test
    fun fallsBackWhenPrimaryFailsBeforeRunningTools() = runTest {
        val primary = FakeAgent("Nenya") {
            addMessage(message(MessageRole.user, "remember the milk"))
            throw AgentNetworkException("offline")
        }
        val fallback = FakeAgent("Cactus") {
            addMessage(message(MessageRole.user, "remember the milk"))
            addMessage(message(MessageRole.tool, "note created"))
        }
        val agent = FallbackAgent(primary, fallback, emptyList())

        agent.sendTestInput(backgroundScope)

        assertEquals(1, fallback.sendCount)
        assertEquals(
            listOf("remember the milk", "note created"),
            agent.conversation.first().map { it.content },
        )
        assertEquals("Cactus", agent.label)
    }

    @Test
    fun doesNotFallBackOnceAToolHasRun() = runTest {
        val primary = FakeAgent("Nenya") {
            addMessage(message(MessageRole.tool, "note created"))
            throw AgentNetworkException("offline")
        }
        val fallback = FakeAgent("Cactus") { }
        val agent = FallbackAgent(primary, fallback, emptyList())

        assertFailsWith<AgentNetworkException> { agent.sendTestInput(backgroundScope) }

        assertEquals(0, fallback.sendCount)
    }

    @Test
    fun onlyCountsToolsFromTheCurrentTurn() = runTest {
        val history = listOf(message(MessageRole.tool, "note created earlier"))
        val primary = FakeAgent("Nenya", history) {
            throw AgentNetworkException("offline")
        }
        val fallback = FakeAgent("Cactus", history) {
            addMessage(message(MessageRole.assistant, "done"))
        }
        val agent = FallbackAgent(primary, fallback, history)

        agent.sendTestInput(backgroundScope)

        assertEquals(1, fallback.sendCount)
        assertTrue(agent.conversation.first().map { it.content }.contains("done"))
    }

    @Test
    fun relaysConversationUpdatesWhileTheActiveAgentRuns() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<Int>()
        val primary = FakeAgent("Nenya") {
            addMessage(message(MessageRole.user, "remember the milk"))
            addMessage(message(MessageRole.assistant, "noted"))
        }
        val agent = FallbackAgent(primary, FakeAgent("Cactus") { }, emptyList())
        val watcher = backgroundScope.launch { agent.conversation.collect { seen.add(it.size) } }

        agent.sendTestInput(backgroundScope)
        watcher.cancel()

        // Each update reaches collectors as it happens, with no duplicate final emission.
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun forwardsAddedMessagesToTheActiveAgent() = runTest {
        val primary = FakeAgent("Nenya") { throw AgentNetworkException("offline") }
        val fallback = FakeAgent("Cactus") { }
        val agent = FallbackAgent(primary, fallback, emptyList())

        agent.sendTestInput(backgroundScope)
        agent.addMessage(message(MessageRole.tool, "forced note"))

        assertEquals(listOf("forced note"), fallback.conversation.first().map { it.content })
        assertEquals(listOf("forced note"), agent.conversation.first().map { it.content })
    }
}
