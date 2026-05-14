package coredevices.ring.agent

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
    fun createForChatMode(
        mode: ChatMode,
        existingConversation: List<ConversationMessageDocument> = emptyList()
    ): Agent {
        val cactusEnabled = prefs.useCactusAgent.value
        return when (mode) {
            ChatMode.Normal -> {
                if (cactusEnabled) {
                    get<AgentCactus> { parametersOf(existingConversation) }
                } else {
                    if (Firebase.auth.currentUser?.emailOrNull == null) {
                        throw AgentAuthenticationException("User must be authenticated to use online LLM agent")
                    }
                    get<AgentNenya> { parametersOf(existingConversation) }
                }
            }
            ChatMode.Search -> {
                if (Firebase.auth.currentUser?.emailOrNull == null) {
                    throw AgentAuthenticationException("User must be authenticated to use search mode")
                }
                get<AgentNenya>() { parametersOf(existingConversation, true) }
            }
        }
    }
}

class AgentAuthenticationException(message: String): Exception(message)

enum class ChatMode {
    Normal,
    Search;
}