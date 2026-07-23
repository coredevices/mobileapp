package coredevices.ring.agent.builtin_servlets.reminders

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
enum class ReminderProvider(val id: Int, val title: String) {
    /** Cross-platform built-in reminders backed by scheduled local notifications. */
    // "Native" is the legacy name for this entry
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("Native")
    BuiltIn(1, "Reminders"),
    GoogleTasks(2, "Google Tasks"),
    /** The native iOS Reminders app (EventKit). iOS-only. */
    IOSReminders(3, "iPhone Reminders"),
    /** Routes reminders to the Tasker automation app via an intent. Android-only. */
    Tasker(4, "Tasker");

    companion object {
        fun fromId(id: Int): ReminderProvider? = entries.find { it.id == id }
    }
}