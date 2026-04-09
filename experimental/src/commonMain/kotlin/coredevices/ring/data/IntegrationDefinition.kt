package coredevices.ring.data

import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider

data class IntegrationDefinition(
    val title: String,
    val reminder: ReminderProvider?,
    val notes: NoteProvider?
)

enum class IntegrationSupport {
    Reminder,
    Note
}