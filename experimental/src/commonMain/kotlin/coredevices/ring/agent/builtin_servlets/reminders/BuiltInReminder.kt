package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.LocalReminderDao
import kotlin.time.Instant

expect fun createBuiltInReminder(time: Instant?, message: String): ListAssignableReminder

expect fun builtInReminderFromData(data: LocalReminderData): ListAssignableReminder

expect fun createRemindersAppReminder(time: Instant?, message: String): ListAssignableReminder

/**
 * Cancels a built-in local reminder by id so a pending notification doesn't still fire once its
 * feed item is completed (MOB-7831). [cancel] removes the scheduled alarm / notification and
 * deletes the [LocalReminderData] row.
 */
suspend fun cancelBuiltInReminder(localReminderId: Int, localReminderDao: LocalReminderDao) {
    val data = localReminderDao.getReminder(localReminderId) ?: return
    runCatching { builtInReminderFromData(data).cancel() }
}
