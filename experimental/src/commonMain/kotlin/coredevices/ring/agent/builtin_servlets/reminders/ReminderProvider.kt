package coredevices.ring.agent.builtin_servlets.reminders

enum class ReminderProvider(val id: Int, val title: String) {
    Native(1, "Reminders"),
    GoogleTasks(2, "Google Tasks");

    companion object {
        fun fromId(id: Int): ReminderProvider? = entries.find { it.id == id }
    }
}