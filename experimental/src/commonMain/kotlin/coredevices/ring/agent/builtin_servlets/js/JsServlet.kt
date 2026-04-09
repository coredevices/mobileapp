package coredevices.ring.agent.builtin_servlets.js

import coredevices.mcp.client.BuiltInMcpIntegration

object JsServlet: BuiltInMcpIntegration(
    name = "builtin_js",
    tools = listOf(
        EvaluateJSTool(),
    )
)