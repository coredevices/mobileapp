@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.repository.ItemRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Marks the feed item backing a fired reminder as done, in response to the user tapping the
 * "Done" action on a reminder notification (MOB-8439).
 *
 * Flipping `done` to true reuses the existing completion pipeline in [ItemRepository.setItem]: on
 * the false->true transition it cancels the scheduled reminder, dismisses the delivered
 * notification, and the change is synced to Firestore by the usual feed-sync observer. So the only
 * work here is resolving the reminder id back to its feed item and writing `done = true`.
 */
class ReminderCompleter(
    private val localReminderDao: LocalReminderDao,
    private val cachedItemDao: CachedItemDao,
    private val itemRepository: ItemRepository,
) {
    /**
     * Marks the feed item linked to [reminderId] as done. Returns true when a backing item was
     * found and newly completed; false when there is nothing to complete (no linked item, or it
     * was already done) — the caller still dismisses the notification in that case.
     *
     * Requires the device to be the same the reminder was created on as it will have the local reminder
     * id. Not to be used for any case other than when we are reacting to local reminder notification.
     */
    suspend fun markDone(reminderId: Int): Boolean {
        val item = runCatching { findItem(reminderId) }.getOrNull() ?: return false
        if (item.done) return false
        itemRepository.setItem(
            item.firestoreId,
            item.toDocument().copy(done = true, updatedAt = Clock.System.now()),
        )
        return true
    }

    /**
     * Resolves [reminderId] to its feed item. `localReminderId` is device-local so this can't be used
     * as a global key and might resolve to the wrong item in extreme edge cases so don't use for anything
     * critical.
     */
    private suspend fun findItem(reminderId: Int): CachedItem? {
        val recordingId = localReminderDao.getReminder(reminderId)?.recordingId
        val candidates =
            if (recordingId != null) cachedItemDao.getByRecording(recordingId)
            else cachedItemDao.getAllActive()
        return candidates.firstOrNull { item ->
            (item.metadata as? ItemMetadata.Reminder)?.localReminderId == reminderId
        }
    }
}
