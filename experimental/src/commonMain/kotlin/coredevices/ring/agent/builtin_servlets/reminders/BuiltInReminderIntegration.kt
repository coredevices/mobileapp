package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.integrations.ReminderIntegration
import kotlin.time.Duration
import kotlin.time.Instant

expect fun createBuiltInReminderIntegration(): BuiltInReminderIntegration

/** The native iOS Reminders app (EventKit). Android returns a disabled stub (never offered there). */
expect fun createRemindersAppIntegration(): ReminderIntegration

/**
 * Cross-platform built-in reminders backed by scheduled local notifications. Each reminder is
 * keyed by its feed item's global Firestore id, so a reminder created on one device (or the web
 * app) resolves to the same alarm wherever the item syncs. A per-device
 * [coredevices.ring.data.entity.room.reminders.LocalReminderData] row records which items have an
 * alarm scheduled here; [ReminderReconciler] arms/disarms them as items sync in.
 */
interface BuiltInReminderIntegration : ReminderIntegration {
    /**
     * Cancels a built-in local reminder so a pending notification doesn't still fire once its feed
     * item is completed (MOB-7831). Removes the scheduled alarm / notification and its ledger row.
     */
    suspend fun cancelReminder(itemId: String)

    /**
     * Cancels only the extra early "heads-up" notification, leaving the main reminder intact.
     * Clears the persisted lead time so it isn't re-scheduled.
     */
    suspend fun cancelExtraNotification(itemId: String)

    /**
     * Schedules (or reschedules) the alarm for the reminder feed item [itemId] to fire at [time],
     * upserting its ledger row. A null or past [time] just cancels any scheduled alarm. Silently
     * skips when notification/exact-alarm permission is missing (unlike [createReminder], which
     * throws) — the reconciler retries on a later pass.
     */
    suspend fun syncReminder(itemId: String, message: String, time: Instant?, notifyBefore: Duration?)

    /**
     * Removes OS schedules left by the pre-global-id scheme (keyed by the old device-local int), so
     * they don't fire alongside the re-armed global-id alarms after upgrade. Run once at startup,
     * before the reconciler arms.
     */
    suspend fun purgeLegacySchedules()
}
