package coredevices.ring.agent.builtin_servlets.bugreport

import coredevices.mcp.client.BuiltInMcpIntegration

class BugReportServlet(createBugReportTool: CreateBugReportTool) : BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        createBugReportTool,
    )
) {
    companion object {
        const val NAME = "builtin_bug_report"
    }
}
