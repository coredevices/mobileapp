@file:OptIn(ExperimentalTime::class)

package coredevices.ring.database.room.repository

import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

/**
 * Verifies that completing (MOB-7831) or deleting (MOB-8390) a reminder item cancels its backing
 * reminder exactly once, and only on a genuine false->true `done`/`deleted` transition.
 */
class ItemRepositoryDoneTransitionTest {

    private class FakeCachedItemDao : CachedItemDao {
        val items = mutableMapOf<String, CachedItem>()
        override suspend fun upsert(item: CachedItem) { items[item.firestoreId] = item }
        override suspend fun upsertAll(items: List<CachedItem>) { items.forEach { this.items[it.firestoreId] = it } }
        override suspend fun getById(id: String): CachedItem? = items[id]
        override fun getByIdFlow(id: String): Flow<CachedItem?> = flowOf(items[id])
        override fun getAllFlow(): Flow<List<CachedItem>> = flowOf(items.values.toList())
        override fun getAllForSyncFlow(): Flow<List<CachedItem>> = flowOf(items.values.toList())
        override fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByRecording(recordingId: String): List<CachedItem> = emptyList()
        override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
        override suspend fun deleteById(id: String) { items.remove(id) }
        override suspend fun deleteAll() { items.clear() }
        override suspend fun getAllIds(): List<String> = items.keys.toList()
        override suspend fun countLocked(): Int = items.values.count { it.locked }
    }

    private fun fixture(): Pair<ItemRepository, MutableList<Int>> {
        val cancelled = mutableListOf<Int>()
        return ItemRepository(FakeCachedItemDao()) { cancelled += it } to cancelled
    }

    private fun reminderItem(done: Boolean, localReminderId: Int? = 7) = ItemDocument(
        title = "Call Lee",
        done = done,
        metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push", localReminderId = localReminderId),
    )

    @Test
    fun completingReminderItemCancelsItsReminderOnce() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = false))
        repo.setItem("a", reminderItem(done = true))
        assertEquals(listOf(7), cancelled)
    }

    @Test
    fun completingReminderItemWithNoLinkedReminderDoesNothing() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = false, localReminderId = null))
        repo.setItem("a", reminderItem(done = true, localReminderId = null))
        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun completingNonReminderItemDoesNothing() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", ItemDocument(title = "note", done = false, metadata = ItemMetadata.Note))
        repo.setItem("a", ItemDocument(title = "note", done = true, metadata = ItemMetadata.Note))
        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun reSavingAlreadyDoneItemDoesNotCancel() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = true))   // new row, already done
        repo.setItem("a", reminderItem(done = true))   // no transition
        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun unCompletingItemDoesNotCancel() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = true))
        repo.setItem("a", reminderItem(done = false))
        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun softDeleteCancelsReminder() = runBlocking {
        // Deleting a reminder item (from the feed, a list, or a recording's
        // "delete recording and items") must cancel the scheduled alarm so the
        // notification doesn't fire for a deleted reminder (MOB-8390).
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = false))
        repo.softDelete("a")
        assertEquals(listOf(7), cancelled)
    }

    @Test
    fun softDeleteOfDoneReminderCancelsAgainHarmlessly() = runBlocking {
        // done=true already cancelled once; the delete transition cancels again,
        // which is a no-op at the alarm layer (row already gone).
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = false))
        repo.setItem("a", reminderItem(done = true))
        repo.softDelete("a")
        assertEquals(listOf(7, 7), cancelled)
    }

    @Test
    fun reSavingAlreadyDeletedItemDoesNotCancel() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", reminderItem(done = false))
        repo.softDelete("a")
        repo.softDelete("a")   // no deleted transition the second time
        assertEquals(listOf(7), cancelled)
    }

    @Test
    fun softDeleteOfNonReminderItemDoesNothing() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.setItem("a", ItemDocument(title = "note", done = false, metadata = ItemMetadata.Note))
        repo.softDelete("a")
        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun syncDownPathNeverCancels() = runBlocking {
        val (repo, cancelled) = fixture()
        repo.upsertLocal("a", reminderItem(done = false))
        repo.upsertLocal("a", reminderItem(done = true))
        assertEquals(emptyList(), cancelled)
    }

    @Test
    fun countLockedReflectsLockedRowsAndUnlocking() = runBlocking {
        val (repo, _) = fixture()
        repo.upsertLocal("a", ItemDocument(title = "x", metadata = ItemMetadata.Note), locked = true)
        repo.upsertLocal("b", ItemDocument(title = "y", metadata = ItemMetadata.Note), locked = false)
        assertEquals(1, repo.countLocked())
        // Re-resolving the locked row with a key (locked = false) clears it.
        repo.upsertLocal("a", ItemDocument(title = "x", metadata = ItemMetadata.Note), locked = false)
        assertEquals(0, repo.countLocked())
    }
}
