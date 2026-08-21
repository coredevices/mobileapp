package coredevices.ring.agent.builtin_servlets.calendar

import co.touchlab.kermit.Logger
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.database.Preferences
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        val connected = phoneCalendarConnected(preferences, permissionRequester).first()
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

/** Phone Calendar is connected only once the user has linked it AND the OS permission is live. */
fun phoneCalendarConnected(
    preferences: Preferences,
    permissionRequester: PermissionRequester,
): Flow<Boolean> = combine(
    preferences.phoneCalendarEnabled,
    permissionRequester.grantedPermissions,
) { enabled, granted -> enabled && Permission.Calendar in granted }
