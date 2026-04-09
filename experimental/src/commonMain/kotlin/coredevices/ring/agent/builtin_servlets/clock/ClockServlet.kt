package coredevices.ring.agent.builtin_servlets.clock

import coredevices.mcp.client.BuiltInMcpIntegration


object ClockServlet: BuiltInMcpIntegration(
    name = "builtin_clock",
    tools = listOf(
        SetAlarmTool(),
        SetTimerTool(),
    )
)