package coredevices.ring.agent

import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/** Runs no model and takes no action, so the input is saved by the caller's forced capture tool. */
class CaptureOnlyAgent(initialConversation: List<ConversationMessageDocument> = emptyList()) : Agent {
    private val _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(initialConversation) }
    override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation
    override val label = "Capture"
    override val isCloud = false

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) = addMessage(ConversationMessageDocument(role = MessageRole.user, content = input))

    override suspend fun addMessage(message: ConversationMessageDocument) {
        _conversation.emit(_conversation.first() + message)
    }
}
