package coredevices.ring.agent

import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.ring.database.Preferences
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
                    get<AgentCactus>() { parametersOf(existingConversation) }
                } else {
                    get<AgentNenya>() { parametersOf(existingConversation) }
                }
            }
            ChatMode.Search -> {
                get<AgentNenya>() { parametersOf(existingConversation, true) }
            }
        }
    }
}

enum class ChatMode {
    Normal,
    Search;
}