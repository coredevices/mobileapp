package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
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
abstract class BuiltInReminderIntegration : ReminderIntegration, KoinComponent {
    private val db: RingDatabase by inject()
    private val feedItems: BuiltInReminderFeedItems by inject()
    private val listRepository: ListRepository by inject()
    private val scheduler: ReminderScheduler by inject()

    /** Why reminders can't be scheduled right now (missing permission), or null if they can. */
    protected abstract suspend fun schedulingBlocker(isGeofence: Boolean): String?

    /**
     * Removes OS schedules left by the pre-global-id scheme (keyed by the old device-local int), so
     * they don't fire alongside the re-armed global-id alarms after upgrade. Run once at startup,
     * before the reconciler arms.
     */
    abstract suspend fun purgeLegacySchedules()

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        require(deadline == null || deadline > Clock.System.now()) { "Time must be in the future" }
        schedulingBlocker(isGeofence = false)?.let { error(it) }

        val itemId = feedItems.newItemId()
        scheduler.scheduleReminder(itemId, title, deadline, notifyBefore)
        db.localReminderDao().upsert(LocalReminderData(itemId, deadline, title, notifyBefore?.inWholeMilliseconds))
        try {
            feedItems.createFeedItem(itemId, title, deadline, listId, notifyBefore, source)
        } catch (e: Exception) {
            cancelReminder(itemId)
            throw e
        }
        return itemId
    }

    /** Creates a reminder that fires on arrival at [location]; armed on this device only. */
    suspend fun createGeofenceReminder(title: String, location: SavedGeofenceLocation, source: ItemSource?): String {
        schedulingBlocker(isGeofence = true)?.let { error(it) }

        val itemId = feedItems.newItemId()
        scheduler.scheduleGeofenceReminder(itemId, title, location.name, location.lat, location.long, location.radiusMeters)
        db.localReminderDao().upsert(
            LocalReminderData(itemId, time = null, message = title, geofenceLocationId = location.id)
        )
        try {
            feedItems.createFeedItem(itemId, title, deadline = null, listId = null, notifyBefore = null, source = source)
        } catch (e: Exception) {
            cancelReminder(itemId)
            throw e
        }
        return itemId
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        feedItems.searchForList(listName)

    override suspend fun getAllLists(): List<ReminderListEntry> =
        listRepository.getAllFlow().first().map { ReminderListEntry(it.firestoreId, it.title) }

    /**
     * Cancels a built-in local reminder so a pending notification doesn't still fire once its feed
     * item is completed. Removes the scheduled alarm / notification and its ledger row.
     */
    suspend fun cancelReminder(itemId: String) {
        scheduler.cancelReminder(itemId)
        db.localReminderDao().deleteReminder(itemId)
    }

    /**
     * Cancels only the extra early "heads-up" notification, leaving the main reminder intact.
     * Clears the persisted lead time so it isn't re-scheduled.
     */
    suspend fun cancelExtraNotification(itemId: String) {
        scheduler.cancelExtraNotification(itemId)
        db.localReminderDao().clearNotifyBefore(itemId)
    }

    /**
     * Schedules (or reschedules) the alarm for the reminder feed item [itemId] to fire at [time],
     * upserting its ledger row. A null or past [time] just cancels any scheduled alarm. Silently
     * skips when notification/exact-alarm permission is missing (unlike [createReminder], which
     * throws) — the reconciler retries on a later pass.
     */
    suspend fun syncReminder(itemId: String, message: String, time: Instant?, notifyBefore: Duration?) {
        if (time == null) {
            val location = db.localReminderDao().getReminder(itemId)?.geofenceLocationId
                ?.let { db.savedGeofenceLocationDao().getById(it) }?.takeUnless { it.deleted }
            if (location != null) return syncGeofenceReminder(itemId, message, location)
        }
        schedulingBlocker(isGeofence = false)?.let {
            logger.w { "Skipping reminder sync: $it" }
            return
        }
        scheduler.cancelReminder(itemId)
        // Schedule before recording the row, so a scheduling failure leaves the ledger stale and
        // the next reconcile retries rather than believing this reminder is armed.
        scheduler.scheduleReminder(itemId, message, time, notifyBefore)
        db.localReminderDao().upsert(LocalReminderData(itemId, time, message, notifyBefore?.inWholeMilliseconds))
    }

    /** As [syncReminder], but armed to fire on arrival at [location]. */
    suspend fun syncGeofenceReminder(itemId: String, message: String, location: SavedGeofenceLocation) {
        schedulingBlocker(isGeofence = true)?.let {
            logger.w { "Skipping geofence reminder sync: $it" }
            return
        }
        scheduler.cancelReminder(itemId)
        scheduler.scheduleGeofenceReminder(itemId, message, location.name, location.lat, location.long, location.radiusMeters)
        db.localReminderDao().upsert(
            LocalReminderData(itemId, time = null, message = message, geofenceLocationId = location.id)
        )
    }

    // Built-in reminders need no account; permissions are checked when scheduling.
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = true

    companion object {
        private val logger = Logger.withTag("BuiltInReminderIntegration")
    }
}
