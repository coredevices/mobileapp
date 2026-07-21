package coredevices.ring.agent.builtin_servlets.calendar

import co.touchlab.kermit.Logger
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.database.Preferences
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
    private val preferences: Preferences by inject()

    override suspend fun getDisabledTools(): List<String> {
        // Phone Calendar is opt-in: the tool only exists once the user has connected the
        // integration in Accounts → Add integration AND calendar permission is granted. Gate on
        // the same PermissionRequester the settings UI uses so the dot and tool availability stay
        // in sync (notably on iOS, where EKAuthorizationStatus can lag a fresh in-session grant).
        val connected = preferences.phoneCalendarEnabled.value &&
            permissionRequester.grantedPermissions.value.contains(Permission.Calendar)
        return if (connected) {
            emptyList()
        } else {
            logger.d {
                "Phone Calendar not connected (enabled=${preferences.phoneCalendarEnabled.value}), disabling calendar tools."
            }
            listOf(CreateCalendarEventTool.TOOL_NAME)
        }
    }
}
