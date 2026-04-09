package coredevices.ring.agent.builtin_servlets.messaging

import co.touchlab.kermit.Logger
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.database.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object MessagingServlet: BuiltInMcpIntegration(
    name = "builtin_messaging",
    tools = listOf(
        SendBeeperMessageTool(),
        SearchBeeperForContactTool()
    )
), KoinComponent {
    private val logger by lazy { Logger.withTag("MessagingServlet") }
    private val prefs: Preferences by inject()
    override suspend fun getDisabledTools(): List<String> {
        val approvedContacts = prefs.approvedBeeperContacts.value.toSet()
        return if (approvedContacts.isEmpty()) {
            logger.d { "No approved contacts for messaging tools, disabling them." }
            listOf(SearchBeeperForContactToolConstants.TOOL_NAME, SendBeeperMessageToolConstants.TOOL_NAME)
        } else {
            emptyList()
        }
    }
}