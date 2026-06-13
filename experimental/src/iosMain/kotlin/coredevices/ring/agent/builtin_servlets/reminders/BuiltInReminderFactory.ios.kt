package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.time.Instant

actual fun createBuiltInReminder(time: Instant?, message: String): ListAssignableReminder {
    return IOSBuiltInReminder(time, message)
}

actual fun builtInReminderFromData(data: LocalReminderData): ListAssignableReminder {
    return IOSBuiltInReminder.fromData(data)
}

actual fun createRemindersAppReminder(time: Instant?, message: String): ListAssignableReminder {
    return IOSRemindersReminder(time, message)
}
