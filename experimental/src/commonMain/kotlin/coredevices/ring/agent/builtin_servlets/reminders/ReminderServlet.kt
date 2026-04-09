package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.mcp.client.BuiltInMcpIntegration

object ReminderServlet: BuiltInMcpIntegration(
    name = "builtin_reminder",
    tools = listOf(
        ReminderTool(),
        ListTool(),
    )
)