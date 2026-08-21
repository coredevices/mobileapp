package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.mcp.client.McpIntegration
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.js.JsServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import coredevices.indexai.data.McpServerDefinition
import coredevices.util.Platform
import coredevices.util.isAndroid
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

const val ANDROID_ONLY_REASON = "Only available on Android"

/**
 * TODO(RING-84): the local Cactus model isn't trained on these tools yet, so they are only usable
 *  via the online Nenya agent. Remove once the on-device model supports them.
 */
val LOCAL_MODEL_UNSUPPORTED_SERVLETS = setOf(CalendarServlet.NAME, MessagingServlet.name)

internal fun builtinServletDefinitions(isAndroid: Boolean): List<McpServerDefinition> {
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
            unavailableReason = androidOnly
        ),
        McpServerDefinition(
            name = MessagingServlet.name,
            title = "Beeper Messaging",
            unavailableReason = androidOnly
        )
    )
}

class BuiltinServletRepository: KoinComponent, ServletRepository {
    private val platform: Platform by inject()

    /**
     * Includes servlets unavailable on this platform so settings can show them greyed out.
     * Anything building a real MCP session must drop entries where [McpServerDefinition.available]
     * is false; [resolveName] returns null for them.
     */
    override fun getAllServlets(): List<McpServerDefinition> =
        builtinServletDefinitions(platform.isAndroid)

    override fun resolveName(name: String): McpIntegration? {
        return when (name) {
            NoteServlet.NAME -> NoteServlet(
                createNoteTool = CreateNoteTool(get())
            )
            ClockServlet.name -> ClockServlet.takeIf { platform.isAndroid }
            JsServlet.name -> JsServlet
            ReminderServlet.NAME -> ReminderServlet(get(), get(), get())
            CalendarServlet.NAME -> CalendarServlet
            MessagingServlet.name -> MessagingServlet.takeIf { platform.isAndroid }
            else -> null
        }
    }
}
