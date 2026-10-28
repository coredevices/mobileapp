package coredevices.ring.agent.builtin_servlets.notifications

import coredevices.mcp.client.BuiltInMcpIntegration

class NotificationReplyServlet(replyTool: ReplyToWatchNotificationTool) : BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        replyTool,
    )
) {
    companion object {
        const val NAME = "builtin_notification_reply"
    }
}
