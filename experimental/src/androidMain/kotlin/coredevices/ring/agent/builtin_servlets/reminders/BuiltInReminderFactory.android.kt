package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.time.Instant

actual fun createBuiltInReminder(time: Instant?, message: String): ListAssignableReminder {
    return AndroidBuiltInReminder(time, message)
}

actual fun builtInReminderFromData(data: LocalReminderData): ListAssignableReminder {
    return AndroidBuiltInReminder.fromData(data)
}

actual fun createRemindersAppReminder(time: Instant?, message: String): ListAssignableReminder {
    throw NotImplementedError() // iOS only
}
