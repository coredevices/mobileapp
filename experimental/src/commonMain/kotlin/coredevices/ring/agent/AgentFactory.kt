package coredevices.ring.agent

import com.cactus.isCactusSupported
import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
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
            ChatMode.Normal -> when {
                !isCactusSupported() -> {
                    if (!signedIn) {
                        throw AgentAuthenticationException("User must be authenticated to use online LLM agent")
                    }
                    remote(existingConversation)
                }
                !signedIn -> local(existingConversation)
                else -> FallbackAgent(
                    primary = remote(existingConversation),
                    fallback = local(existingConversation),
                    initialConversation = existingConversation,
                )
            }
            ChatMode.Local -> local(existingConversation)
            // Always online, because, well, search
            ChatMode.Search -> {
                if (!signedIn) {
                    throw AgentAuthenticationException("User must be authenticated to use search mode")
                }
                get<SearchAgentNenya> { parametersOf(existingConversation) }
            }
        }
    }
}

class AgentAuthenticationException(message: String): Exception(message)

sealed interface ChatMode {
    /** The Index agent: online (Nenya), falling back to on-device (Cactus) where supported. */
    data object Normal : ChatMode
    /** The on-device (Cactus) Index agent only. */
    data object Local : ChatMode
    data object Search : ChatMode
}
