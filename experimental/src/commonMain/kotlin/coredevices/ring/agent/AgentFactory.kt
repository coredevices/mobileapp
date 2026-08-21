package coredevices.ring.agent

import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.ring.api.NenyaModel
import coredevices.ring.database.Preferences
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class AgentFactory: KoinComponent {
    private val prefs by inject<Preferences>()

    private val signedIn get() = Firebase.auth.currentUser?.emailOrNull != null

    private fun local(conversation: List<ConversationMessageDocument>): Agent =
        get<IndexAgentCactus> { parametersOf(conversation) }

    private fun remote(conversation: List<ConversationMessageDocument>): Agent =
        get<IndexAgentNenya> { parametersOf(conversation, prefs.defaultCaptureType.value) }

    fun createForChatMode(
        mode: ChatMode,
        existingConversation: List<ConversationMessageDocument> = emptyList()
    ): Agent {
        return when (mode) {
            ChatMode.Normal -> {
                when (prefs.llmMode.value) {
                    LlmMode.LocalOnly -> local(existingConversation)
                    LlmMode.RemoteOnly -> {
                        if (!signedIn) {
                            throw AgentAuthenticationException("User must be authenticated to use online LLM agent")
                        }
                        remote(existingConversation)
                    }
                    LlmMode.RemoteFirst -> if (!signedIn) {
                        local(existingConversation)
                    } else {
                        FallbackAgent(
                            primary = remote(existingConversation),
                            fallback = local(existingConversation),
                            initialConversation = existingConversation,
                        )
                    }
                }
            }
            // Always online, because, well, search
            ChatMode.Search -> {
                if (Firebase.auth.currentUser?.emailOrNull == null) {
                    throw AgentAuthenticationException("User must be authenticated to use search mode")
                }
                get<SearchAgentNenya> { parametersOf(existingConversation) }
            }
            is ChatMode.McpSandbox -> {
                when (mode.group.modelType) {
                    // IndexAgent groups use the standard Index agent path
                    SandboxModelType.IndexAgent ->
                        createForChatMode(ChatMode.Normal, existingConversation)
                    SandboxModelType.Default, SandboxModelType.HighCapability -> {
                        if (Firebase.auth.currentUser?.emailOrNull == null) {
                            throw AgentAuthenticationException("User must be authenticated to use MCP sandbox mode")
                        }
                        val model = when (mode.group.modelType) {
                            SandboxModelType.HighCapability -> NenyaModel.HighCapability
                            else -> NenyaModel.Default
                        }
                        get<McpSandboxAgentNenya> { parametersOf(model, existingConversation) }
                    }
                }
            }
        }
    }
}

class AgentAuthenticationException(message: String): Exception(message)

/** How a [ChatMode.Normal] chat picks between the online (Nenya) and on-device (Cactus) LLM. */
enum class LlmMode(val id: Int) {
    RemoteOnly(0),
    LocalOnly(1),
    RemoteFirst(2);

    companion object {
        fun fromId(id: Int): LlmMode = entries.firstOrNull { it.id == id } ?: RemoteOnly
    }

    fun usesLocalCactus(): Boolean = this == LocalOnly || this == RemoteFirst
}

sealed interface ChatMode {
    data object Normal : ChatMode
    data object Search : ChatMode
    /** Agent driven by a specific MCP sandbox group's servers and model type. */
    data class McpSandbox(val group: McpSandboxGroupEntity) : ChatMode
}