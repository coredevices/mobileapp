package coredevices.ring.agent.builtin_servlets.reminders

enum class ReminderProvider(val id: Int, val title: String) {
    /** Cross-platform built-in reminders backed by scheduled local notifications. */
    BuiltIn(1, "Reminders"),
    GoogleTasks(2, "Google Tasks"),
    /** The native iOS Reminders app (EventKit). iOS-only. */
    IOSReminders(3, "iPhone Reminders");

    companion object {
        fun fromId(id: Int): ReminderProvider? = entries.find { it.id == id }
    }
}