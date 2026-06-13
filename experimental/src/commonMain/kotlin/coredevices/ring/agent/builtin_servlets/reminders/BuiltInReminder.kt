package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.time.Instant

expect fun createBuiltInReminder(time: Instant?, message: String): ListAssignableReminder

expect fun builtInReminderFromData(data: LocalReminderData): ListAssignableReminder

expect fun createRemindersAppReminder(time: Instant?, message: String): ListAssignableReminder
