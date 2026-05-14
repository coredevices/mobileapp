package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.ring.database.Preferences
import kotlin.time.Instant

class ReminderFactory(private val preferences: Preferences) {
    companion object {
        private val logger = Logger.withTag("ReminderFactory")
    }
    fun create(time: Instant?, message: String, integration: ReminderProvider = preferences.reminderProvider.value): Reminder {
        logger.i { "Creating reminder integration for provider: $integration" }
        return when (integration) {
            ReminderProvider.Native -> createNativeReminder(time, message)
            ReminderProvider.GoogleTasks -> GTasksReminder(time, message)
        }
    }
}