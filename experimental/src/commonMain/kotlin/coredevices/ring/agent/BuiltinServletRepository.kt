package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.mcp.client.McpIntegration
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

class BuiltinServletRepository: KoinComponent, ServletRepository {
    private val platform: Platform by inject()

    override fun getAllServlets(): List<McpServerDefinition> {
        return buildList {
            addAll(
                listOf(
                    McpServerDefinition(
                        name = NoteServlet.NAME,
                        title = "Note Creation"
                    ),
                    /*McpServerDefinition(
                        name = JsServlet.name,
                        title = "JavaScript Evaluation"
                    ),*/
                    McpServerDefinition(
                        name = ReminderServlet.name,
                        title = "Reminders"
                    )
                )
            )
            if (platform.isAndroid) {
                addAll(
                    listOf(
                        McpServerDefinition(
                            name = ClockServlet.name,
                            title = "Timers & Alarms"
                        ),
                        McpServerDefinition(
                            name = MessagingServlet.name,
                            title = "Beeper Messaging"
                        )
                    )
                )
            }
        }
    }

    override fun resolveName(name: String): McpIntegration? {
        return when (name) {
            NoteServlet.NAME -> NoteServlet(
                createNoteTool = CreateNoteTool(get())
            )
            ClockServlet.name -> ClockServlet
            JsServlet.name -> JsServlet
            ReminderServlet.name -> ReminderServlet
            MessagingServlet.name -> {
                require(platform.isAndroid) { "Messaging servlet is only available on Android" }
                MessagingServlet
            }
            else -> null
        }
    }
}