package coredevices.ring.agent.builtin_servlets.notes

import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.agent.builtin_servlets.destinationHints
import coredevices.ring.agent.builtin_servlets.userMessageIfReady
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport

class NoteServlet(
    createNoteTool: CreateNoteTool,
    private val providers: IntegrationProviderRepository,
): BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        createNoteTool,
    )
) {
    companion object {
        const val NAME = "builtin_note"
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val hints = providers.destinationHints(
            sessionContext.userMessageIfReady(), IntegrationSupport.Note, CreateNoteTool.TOOL_NAME,
        )
        return listOfNotNull(super.getExtraContext(sessionContext))
            .plus(hints)
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
    }
}
