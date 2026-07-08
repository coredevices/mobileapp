package coredevices.ring.agent.builtin_servlets.messaging

import co.touchlab.kermit.Logger
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.database.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object MessagingServlet: BuiltInMcpIntegration(
    name = "builtin_messaging",
    tools = listOf(
        SendBeeperMessageTool(),
    )
), KoinComponent {
    private val logger by lazy { Logger.withTag("MessagingServlet") }
    private val prefs: Preferences by inject()
    override suspend fun getDisabledTools(): List<String> {
        val approvedContacts = prefs.approvedBeeperContacts.value
        return if (approvedContacts.isEmpty()) {
            logger.d { "No approved contacts for messaging tools, disabling them." }
            listOf(SendBeeperMessageToolConstants.TOOL_NAME)
        } else {
            emptyList()
        }
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        return if (prefs.approvedBeeperContacts.value.isNotEmpty()) {
            buildString {
                appendLine(super.getExtraContext(sessionContext))
                appendLine("Approved contacts for ${SendBeeperMessageToolConstants.TOOL_NAME}:")
                prefs.approvedBeeperContacts.value.forEach { contact ->
                    appendLine("- ${contact.name} ${contact.nickname?.let { "(Nickname: $it)" } ?: ""}".trim())
                }
            }
        } else {
            super.getExtraContext(sessionContext)
        }
    }
}