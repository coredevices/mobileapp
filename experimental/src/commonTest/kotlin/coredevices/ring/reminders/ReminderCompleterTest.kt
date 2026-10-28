@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Verifies the "Done" notification action path (MOB-8439): completing a fired reminder's feed item
 * by its global id through [ItemRepository], which in turn cancels the backing reminder.
 */
class ReminderCompleterTest {

    private class FakeCachedItemDao : CachedItemDao {
        val items = mutableMapOf<String, CachedItem>()
        override suspend fun upsert(item: CachedItem) { items[item.firestoreId] = item }
        override suspend fun upsertAll(items: List<CachedItem>) { items.forEach { this.items[it.firestoreId] = it } }
        override suspend fun getById(id: String): CachedItem? = items[id]
        override fun getByIdFlow(id: String): Flow<CachedItem?> = flowOf(items[id])
        override fun getAllFlow(): Flow<List<CachedItem>> = flowOf(items.values.toList())
        override fun getAllForSyncFlow(): Flow<List<CachedItem>> = flowOf(items.values.toList())
        override fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>> =
            flowOf(items.values.filter { it.sourceRecordingId == recordingId && !it.deleted })
        override suspend fun getByRecording(recordingId: String): List<CachedItem> =
            items.values.filter { it.sourceRecordingId == recordingId && !it.deleted }
        override suspend fun getAllActive(): List<CachedItem> = items.values.filter { !it.deleted }
        override suspend fun getAllIncludingDeleted(): List<CachedItem> = items.values.toList()
        override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
        override suspend fun deleteById(id: String) { items.remove(id) }
        override suspend fun deleteAll() { items.clear() }
        override suspend fun getAllIds(): List<String> = items.keys.toList()
        override suspend fun countLocked(): Int = items.values.count { it.locked }
    }

    private class Fixture {
        val itemDao = FakeCachedItemDao()
        val cancelled = mutableListOf<String>()
        val repo = ItemRepository(itemDao, cancelReminder = { cancelled += it })
        val completer = ReminderCompleter(repo)
    }

    private fun reminderItem(firestoreId: String, done: Boolean = false) = CachedItem(
        firestoreId = firestoreId,
        title = "Call Lee",
        done = done,
        metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push"),
    )

    @Test
    fun markDoneCompletesItemAndCancelsReminder() = runBlocking {
        val f = Fixture()
        f.itemDao.items["item1"] = reminderItem("item1")

        val result = f.completer.markDone("item1")

        assertTrue(result)
        assertTrue(f.itemDao.items["item1"]!!.done, "item should be marked done")
        assertEquals(listOf("item1"), f.cancelled, "backing reminder should be cancelled exactly once")
    }

    @Test
    fun markDoneReturnsFalseWhenNoSuchItem() = runBlocking {
        val f = Fixture()
        val result = f.completer.markDone("missing")
        assertFalse(result)
        assertEquals(emptyList(), f.cancelled)
    }

    @Test
    fun markDoneIsNoOpWhenAlreadyDone() = runBlocking {
        val f = Fixture()
        f.itemDao.items["item1"] = reminderItem("item1", done = true)
        val result = f.completer.markDone("item1")
        assertFalse(result, "already-done item should not transition again")
        assertEquals(emptyList(), f.cancelled)
    }

    @Test
    fun markDoneIgnoresDeletedItems() = runBlocking {
        val f = Fixture()
        f.itemDao.items["item1"] = reminderItem("item1").copy(deleted = true)
        val result = f.completer.markDone("item1")
        assertFalse(result, "a deleted reminder must not be completed")
        assertEquals(emptyList(), f.cancelled)
    }
}
