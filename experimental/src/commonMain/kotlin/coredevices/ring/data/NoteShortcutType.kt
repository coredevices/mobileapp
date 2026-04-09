package coredevices.ring.data

import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import kotlinx.serialization.Serializable

@Serializable
sealed interface NoteShortcutType {
    @Serializable
    data object SendToMe: NoteShortcutType
    @Serializable
    data class SendToNoteProvider(val provider: NoteProvider): NoteShortcutType
    @Serializable
    data class SendToReminderProvider(val provider: ReminderProvider): NoteShortcutType
}