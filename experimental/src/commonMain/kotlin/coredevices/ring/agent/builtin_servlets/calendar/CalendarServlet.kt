package coredevices.ring.agent.builtin_servlets.calendar

import co.touchlab.kermit.Logger
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object CalendarServlet : BuiltInMcpIntegration(
    name = "builtin_calendar",
    tools = listOf(
        CreateCalendarEventTool(),
    )
), KoinComponent {
    const val NAME = "builtin_calendar"
    private val logger by lazy { Logger.withTag("CalendarServlet") }
    private val permissionRequester: PermissionRequester by inject()

    override suspend fun getDisabledTools(): List<String> {
        // The calendar account is "connected" once the user has granted calendar permission
        // (tapping the Calendar dot in Index settings requests it). Gate on the same
        // PermissionRequester the settings UI uses so the dot and tool availability stay in sync
        // (notably on iOS, where EKAuthorizationStatus can lag a fresh in-session grant).
        return if (permissionRequester.grantedPermissions.value.contains(Permission.Calendar)) {
            emptyList()
        } else {
            logger.d { "Calendar permission not granted, disabling calendar tools." }
            listOf(CreateCalendarEventTool.TOOL_NAME)
        }
    }
}
