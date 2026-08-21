package coredevices.ring.agent

import coredevices.indexai.util.JsonSnake
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.reminders.ReminderTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What Index captures when a request is ambiguous or the agent took no other action.
 * The destination is the existing [coredevices.ring.database.Preferences.noteProvider] /
 * [coredevices.ring.database.Preferences.reminderProvider].
 */
enum class DefaultCaptureType(
    val id: Int,
    val title: String,
    /** Noun substituted into the agent prompt's fallback guidance. */
    val promptNoun: String,
) {
    Note(1, "Note", "note"),
    Reminder(2, "Reminder", "reminder");

    companion object {
        fun fromId(id: Int): DefaultCaptureType = entries.firstOrNull { it.id == id } ?: Note
    }
}

/** Built-in tool and JSON arguments a forced fallback capture runs. */
data class FallbackToolCall(val toolName: String, val arguments: String)

fun DefaultCaptureType.fallbackToolCall(messageText: String): FallbackToolCall = when (this) {
    DefaultCaptureType.Note -> FallbackToolCall(
        CreateNoteTool.TOOL_NAME,
        JsonSnake.encodeToString(
            CreateNoteTool.CreateNoteArgs(text = messageText, automatic = true)
        ),
    )
    DefaultCaptureType.Reminder -> FallbackToolCall(
        ReminderTool.TOOL_NAME,
        buildJsonObject { put("message", messageText) }.toString(),
    )
}
