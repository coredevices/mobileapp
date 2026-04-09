package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.time.Instant

actual fun createNativeReminder(time: Instant?, message: String): ListAssignableReminder {
    return AndroidBuiltInReminder(time, message)
}

actual fun nativeReminderFromData(data: LocalReminderData): ListAssignableReminder {
    return AndroidBuiltInReminder.fromData(data)
}
