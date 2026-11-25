package coredevices.ring.database.room.repository

import co.touchlab.kermit.Logger
import coredevices.ring.agent.builtin_servlets.reminders.BuiltInReminderIntegration
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.dao.SavedGeofenceLocationDao
import coredevices.ring.reminders.ReminderScheduler
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Saved places. Writes land in Room and are pushed to Firestore by
 * [coredevices.ring.service.indexfeed.GeofenceLocationSyncService]; deletes are tombstones.
 */
class GeofenceLocationRepository(
    private val dao: SavedGeofenceLocationDao,
    private val localReminderDao: LocalReminderDao,
    private val scheduler: ReminderScheduler,
    private val itemRepository: ItemRepository,
    private val reminders: BuiltInReminderIntegration,
) {
    suspend fun insertUpdateSaved(entity: SavedGeofenceLocation) =
        store(entity.copy(updatedAt = Clock.System.now(), deleted = false))

    /** Stores a synced-in [entity] with its remote timestamp, disarming reminders at a deleted place. */
    suspend fun applyRemote(entity: SavedGeofenceLocation) {
        if (entity.deleted) disarmReminders(entity.id)
        store(entity)
    }

    suspend fun reminderCount(uuid: Uuid) = localReminderDao.getForGeofenceLocation(uuid).size

    /** Deletes the location along with every reminder armed at it. */
    suspend fun deleteSavedWithReminders(uuid: Uuid) {
        localReminderDao.getForGeofenceLocation(uuid).forEach {
            scheduler.cancelReminder(it.itemId)
            itemRepository.softDelete(it.itemId)
        }
        localReminderDao.clearGeofenceLocation(uuid)
        dao.markDeleted(uuid, Clock.System.now())
    }

    /** Deletes the location; reminders armed at it are disarmed and left as timeless reminders. */
    suspend fun deleteSavedKeepingReminders(uuid: Uuid) {
        disarmReminders(uuid)
        dao.markDeleted(uuid, Clock.System.now())
    }

    /** Drops the row outright, for a place hard-deleted from Firestore. */
    suspend fun removeLocal(uuid: Uuid) {
        disarmReminders(uuid)
        dao.deleteById(uuid)
    }

    suspend fun getSavedById(uuid: Uuid) = dao.getById(uuid)?.takeUnless { it.deleted }
    suspend fun getForSync(uuid: Uuid) = dao.getById(uuid)
    fun getAllFlow() = dao.getAllFlow()
    fun getAllForSyncFlow() = dao.getAllForSyncFlow()

    private suspend fun store(entity: SavedGeofenceLocation) {
        val previous = dao.getById(entity.id)
        dao.insertUpdate(entity)
        if (entity.deleted || previous == null || previous.deleted) return
        val moved = previous.lat != entity.lat || previous.long != entity.long ||
            previous.radiusMeters != entity.radiusMeters || previous.name != entity.name
        if (moved) rearmReminders(entity)
    }

    private suspend fun rearmReminders(entity: SavedGeofenceLocation) {
        localReminderDao.getForGeofenceLocation(entity.id).forEach {
            try {
                reminders.syncGeofenceReminder(it.itemId, it.message, entity)
            } catch (e: Exception) {
                logger.e(e) { "Failed to re-arm reminder ${it.itemId} at ${entity.id}" }
            }
        }
    }

    private suspend fun disarmReminders(uuid: Uuid) {
        localReminderDao.getForGeofenceLocation(uuid).forEach { scheduler.cancelReminder(it.itemId) }
        localReminderDao.clearGeofenceLocation(uuid)
    }

    companion object {
        private val logger = Logger.withTag("GeofenceLocationRepository")
    }
}
