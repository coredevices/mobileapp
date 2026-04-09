package coredevices.ring.agent.builtin_servlets.notes

import coredevices.mcp.client.BuiltInMcpIntegration

class NoteServlet(createNoteTool: CreateNoteTool): BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        createNoteTool,
    )
) {
    companion object {
        const val NAME = "builtin_note"
    }
}