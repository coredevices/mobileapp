package coredevices.indexai.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.Serializable

/** One inference round: what the model was given. */
data class AgentIteration(
    val agent: String,
    val toolDeclarations: List<ToolDeclarationRecord>,
    val conversation: List<ConversationMessageDocument>,
)

@Serializable
data class ToolDeclarationRecord(
    val integrationName: String,
    val definition: Tool,
)
