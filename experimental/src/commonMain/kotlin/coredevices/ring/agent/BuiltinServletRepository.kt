package coredevices.ring.agent

import com.russhwolf.settings.Settings
import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.mcp.client.McpIntegration
import coredevices.pebble.ui.showDebugOptions
import coredevices.ring.agent.builtin_servlets.bugreport.BugReportServlet
import coredevices.ring.agent.builtin_servlets.bugreport.CreateBugReportTool
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.clock.clockUnavailableReason
import coredevices.ring.agent.builtin_servlets.js.JsServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notifications.NotificationReplyServlet
import coredevices.ring.agent.builtin_servlets.notifications.ReplyToWatchNotificationTool
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import coredevices.util.Platform
import coredevices.util.isAndroid
import io.rebble.libpebblecommon.connection.LibPebble
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

const val ANDROID_ONLY_REASON = "Only available on Android"

internal fun builtinServletDefinitions(
    isAndroid: Boolean,
    clockUnavailableReason: String? = null,
): List<McpServerDefinition> {
    val androidOnly = if (isAndroid) null else ANDROID_ONLY_REASON
    return listOf(
        McpServerDefinition(
            name = NoteServlet.NAME,
            title = "Note Creation"
        ),
        McpServerDefinition(
            name = ReminderServlet.NAME,
            title = "Reminders"
        ),
        McpServerDefinition(
            name = CalendarServlet.NAME,
            title = "Calendar"
        ),
        McpServerDefinition(
            name = ClockServlet.name,
            title = "Timers & Alarms",
            unavailableReason = clockUnavailableReason
        ),
        McpServerDefinition(
            name = MessagingServlet.name,
            title = "Beeper Messaging",
            unavailableReason = androidOnly
        ),
        McpServerDefinition(
            name = NotificationReplyServlet.NAME,
            title = "Reply to Notifications",
            unavailableReason = androidOnly
        ),
        McpServerDefinition(
            name = BugReportServlet.NAME,
            title = "Bug Reports"
        )
    )
}

class BuiltinServletRepository: KoinComponent, ServletRepository {
    private val platform: Platform by inject()
    private val settings: Settings by inject()

    /**
     * Includes servlets unavailable on this platform so settings can show them greyed out.
     * Anything building a real MCP session must drop entries where [McpServerDefinition.available]
     * is false; [resolveName] returns null for them.
     */
    override fun getAllServlets(): List<McpServerDefinition> =
        builtinServletDefinitions(platform.isAndroid, clockUnavailableReason())
            .filter { it.name != BugReportServlet.NAME || settings.showDebugOptions() }

    override fun resolveName(name: String): McpIntegration? {
        return when (name) {
            NoteServlet.NAME -> NoteServlet(
                createNoteTool = CreateNoteTool(get()),
                providers = get(),
            )
            ClockServlet.name -> ClockServlet.takeIf { clockUnavailableReason() == null }
            JsServlet.name -> JsServlet
            ReminderServlet.NAME -> ReminderServlet(get(), get(), get())
            CalendarServlet.NAME -> CalendarServlet
            MessagingServlet.name -> MessagingServlet.takeIf { platform.isAndroid }
            NotificationReplyServlet.NAME -> NotificationReplyServlet(
                replyTool = ReplyToWatchNotificationTool(get<LibPebble>())
            ).takeIf { platform.isAndroid }
            BugReportServlet.NAME -> BugReportServlet(CreateBugReportTool(get())).takeIf { settings.showDebugOptions() }
            else -> null
        }
    }
}
