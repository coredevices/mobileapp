@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.ring.database.room.repository.ItemRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Marks the feed item backing a fired reminder as done, in response to the user tapping the
 * "Done" action on a reminder notification (MOB-8439).
 *
 * Flipping `done` to true reuses the existing completion pipeline in [ItemRepository.setItem]: on
 * the false->true transition it cancels the scheduled reminder, dismisses the delivered
 * notification, and the change is synced to Firestore by the usual feed-sync observer.
 */
class ReminderCompleter(
    private val itemRepository: ItemRepository,
) {
    /**
     * Marks the feed item [itemId] as done. Returns true when the item was found and newly
     * completed; false when there is nothing to complete (no such item, or it was already done) —
     * the caller still dismisses the notification in that case.
     */
    suspend fun markDone(itemId: String): Boolean {
        val item = runCatching { itemRepository.getById(itemId) }.getOrNull() ?: return false
        if (item.done || item.deleted) return false
        itemRepository.setItem(
            item.firestoreId,
            item.toDocument().copy(done = true, updatedAt = Clock.System.now()),
        )
        return true
    }
}
