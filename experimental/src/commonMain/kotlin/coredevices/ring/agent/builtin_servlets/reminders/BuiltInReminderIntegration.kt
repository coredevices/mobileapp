package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.integrations.ReminderIntegration
import kotlin.time.Instant

expect fun createBuiltInReminderIntegration(): BuiltInReminderIntegration

/** The native iOS Reminders app (EventKit). Android returns a disabled stub (never offered there). */
expect fun createRemindersAppIntegration(): ReminderIntegration

/**
 * Cross-platform built-in reminders backed by scheduled local notifications, each recorded as a
 * [coredevices.ring.data.entity.room.reminders.LocalReminderData] row whose id is the reminder id.
 */
interface BuiltInReminderIntegration : ReminderIntegration {
    /**
     * Cancels a built-in local reminder by id so a pending notification doesn't still fire once
     * its feed item is completed (MOB-7831). Removes the scheduled alarm / notification and
     * deletes the reminder's row.
     */
    suspend fun cancelReminder(reminderId: Int)

    /**
     * Cancels only the extra early "heads-up" notification, leaving the main reminder (and its
     * row) intact. Clears the persisted lead time so it isn't re-scheduled after a reboot.
     */
    suspend fun cancelExtraNotification(reminderId: Int)

    /**
     * Moves a built-in reminder to [newTime], replacing any scheduled or already-fired
     * notification so it fires again at the new time. A null [newTime] just cancels the
     * scheduled notification, keeping the reminder's row. [expectedRecordingId] must match
     * the local row's recordingId — reminder ids are device-local, so an id synced from
     * another device can collide with an unrelated local reminder.
     */
    suspend fun rescheduleReminder(reminderId: Int, expectedRecordingId: String?, newTime: Instant?)
}
