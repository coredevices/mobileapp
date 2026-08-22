package coredevices.ring.agent.builtin_servlets.notes

import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.data.IntegrationDefinition

/**
 * Notesnook is an Android-only local notes app. Recordings are delivered with an explicit
 * intent (audio + transcription, no internet). iOS returns a disabled stub so the provider
 * never surfaces there.
 */
expect fun createNotesnookNoteClient(): NoteIntegration

val NOTESNOOK_DEFINITION = IntegrationDefinition(
    title = "Notesnook",
    reminder = ReminderProvider.Notesnook,
    notes = NoteProvider.Notesnook,
)

const val NOTESNOOK_TOKEN_STORAGE_KEY = "notesnook"
