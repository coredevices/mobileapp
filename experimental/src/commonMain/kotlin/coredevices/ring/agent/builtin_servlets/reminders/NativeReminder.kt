package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.time.Instant

expect fun createNativeReminder(time: Instant?, message: String): ListAssignableReminder

expect fun nativeReminderFromData(data: LocalReminderData): ListAssignableReminder
