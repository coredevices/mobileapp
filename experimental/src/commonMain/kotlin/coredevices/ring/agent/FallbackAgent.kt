package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [primary] and, if it fails, re-runs the same input on [fallback], republishing
 * whichever agent is active through a single conversation flow.
 */
class FallbackAgent(
    private val primary: Agent,
    private val fallback: Agent,
    initialConversation: List<ConversationMessageDocument>,
) : Agent {
    private val logger = Logger.withTag("FallbackAgent")

    private val _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(initialConversation) }
    override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation
    private var published: List<ConversationMessageDocument> = initialConversation

    private val initialSize = initialConversation.size
    private var active: Agent = primary
    override val label get() = active.label

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) {
        try {
            runRelaying(primary) {
                it.send(input, mcpSession, sessionContext, includePromptsFromMcps, skipToolExecution)
            }
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Re-running a turn whose tools already fired would repeat their side effects.
            if (primary.conversation.first().drop(initialSize).any { it.role == MessageRole.tool }) throw e
            logger.w(e) { "${primary.label} agent failed, falling back to ${fallback.label}: ${e.message}" }
        }
        runRelaying(fallback) {
            it.send(input, mcpSession, sessionContext, includePromptsFromMcps, skipToolExecution)
        }
    }

    override suspend fun addMessage(message: ConversationMessageDocument) {
        active.addMessage(message)
        publish(active.conversation.first())
    }

    /** Republish [agent]'s conversation while [block] runs, then publish its final state. */
    private suspend fun runRelaying(agent: Agent, block: suspend (Agent) -> Unit) {
        active = agent
        coroutineScope {
            val relay = launch { agent.conversation.collect { publish(it) } }
            try {
                block(agent)
            } finally {
                withContext(NonCancellable) {
                    relay.cancelAndJoin()
                    // The relay may not have drained the last emission before it was
                    // cancelled, and callers read the conversation once send() returns.
                    publish(agent.conversation.first())
                }
            }
        }
    }

    private suspend fun publish(messages: List<ConversationMessageDocument>) {
        if (messages == published) return
        published = messages
        _conversation.emit(messages)
    }
}
