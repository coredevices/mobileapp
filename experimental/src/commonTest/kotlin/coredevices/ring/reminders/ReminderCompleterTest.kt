@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.repository.ItemRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Verifies the "Done" notification action path (MOB-8439): resolving a fired reminder back to its
 * feed item by `localReminderId` and completing it through [ItemRepository], which in turn cancels
 * the backing reminder.
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
        override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
        override suspend fun deleteById(id: String) { items.remove(id) }
        override suspend fun deleteAll() { items.clear() }
        override suspend fun getAllIds(): List<String> = items.keys.toList()
        override suspend fun countLocked(): Int = items.values.count { it.locked }
    }

    private class FakeLocalReminderDao : LocalReminderDao {
        val reminders = mutableMapOf<Int, LocalReminderData>()
        override suspend fun insertReminder(reminder: LocalReminderData): Long {
            reminders[reminder.id] = reminder
            return reminder.id.toLong()
        }
        override suspend fun getAllReminders(): List<LocalReminderData> = reminders.values.toList()
        override suspend fun getAllRemindersInRange(start: Instant, end: Instant): List<LocalReminderData> =
            reminders.values.filter { it.time != null && it.time!! >= start && it.time!! <= end }
        override fun getAllRemindersFlow(): Flow<List<LocalReminderData>> = flowOf(reminders.values.toList())
        override suspend fun getReminder(id: Int): LocalReminderData? = reminders[id]
        override suspend fun setRecordingId(id: Int, recordingId: String) {
            reminders[id]?.let { reminders[id] = it.copy(recordingId = recordingId) }
        }
        override suspend fun clearNotifyBefore(id: Int) {
            reminders[id]?.let { reminders[id] = it.copy(notifyBeforeMillis = null) }
        }
        override suspend fun setTime(id: Int, time: Instant?) {
            reminders[id]?.let { reminders[id] = it.copy(time = time) }
        }
        override suspend fun deleteReminder(id: Int) { reminders.remove(id) }
    }

    private class Fixture {
        val itemDao = FakeCachedItemDao()
        val reminderDao = FakeLocalReminderDao()
        val cancelled = mutableListOf<Int>()
        val repo = ItemRepository(itemDao, cancelReminder = { cancelled += it })
        val completer = ReminderCompleter(reminderDao, itemDao, repo)

        /** Register a local reminder row so the completer can resolve its source recording. */
        fun addReminder(id: Int, recordingId: String? = null) {
            reminderDao.reminders[id] = LocalReminderData(id = id, time = null, message = "", recordingId = recordingId)
        }
    }

    private fun reminderItem(
        firestoreId: String,
        localReminderId: Int,
        recordingId: String? = null,
        done: Boolean = false,
    ) = CachedItem(
        firestoreId = firestoreId,
        title = "Call Lee",
        done = done,
        sourceRecordingId = recordingId,
        metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push", localReminderId = localReminderId),
    )

    @Test
    fun markDoneCompletesLinkedItemAndCancelsReminder() = runBlocking {
        val f = Fixture()
        f.addReminder(5, recordingId = "rec1")
        f.itemDao.items["item1"] = reminderItem("item1", localReminderId = 5, recordingId = "rec1")

        val result = f.completer.markDone(5)

        assertTrue(result)
        assertTrue(f.itemDao.items["item1"]!!.done, "item should be marked done")
        assertEquals(listOf(5), f.cancelled, "backing reminder should be cancelled exactly once")
    }

    @Test
    fun markDoneCompletesReminderItemWithNoSourceRecording() = runBlocking {
        // Reminders created via ShareActionHandler have sourceRecordingId == null but still carry
        // localReminderId in metadata — they must be completable from the notification too.
        val f = Fixture()
        f.addReminder(5, recordingId = null)
        f.itemDao.items["item1"] = reminderItem("item1", localReminderId = 5, recordingId = null)

        val result = f.completer.markDone(5)

        assertTrue(result)
        assertTrue(f.itemDao.items["item1"]!!.done)
        assertEquals(listOf(5), f.cancelled)
    }

    @Test
    fun markDoneReturnsFalseWhenNoItemLinksToReminder() = runBlocking {
        val f = Fixture()
        f.itemDao.items["item1"] = reminderItem("item1", localReminderId = 99)
        val result = f.completer.markDone(5)
        assertFalse(result)
        assertEquals(emptyList(), f.cancelled)
    }

    @Test
    fun markDoneReturnsFalseWhenNoItemsAtAll() = runBlocking {
        val f = Fixture()
        val result = f.completer.markDone(5)
        assertFalse(result)
        assertEquals(emptyList(), f.cancelled)
    }

    @Test
    fun markDoneIsNoOpWhenAlreadyDone() = runBlocking {
        val f = Fixture()
        f.itemDao.items["item1"] = reminderItem("item1", localReminderId = 5, done = true)
        val result = f.completer.markDone(5)
        assertFalse(result, "already-done item should not transition again")
        assertEquals(emptyList(), f.cancelled)
    }

    @Test
    fun markDoneIgnoresDeletedItems() = runBlocking {
        val f = Fixture()
        f.itemDao.items["item1"] = reminderItem("item1", localReminderId = 5).copy(deleted = true)
        val result = f.completer.markDone(5)
        assertFalse(result)
        assertEquals(emptyList(), f.cancelled)
    }

    @Test
    fun markDoneOnlyCompletesTheLocalReminderRecordingWhenIdsCollide() = runBlocking {
        // A remote item synced from another device can carry the same localReminderId as the
        // reminder that fired here. When the local reminder records its source recording, the lookup
        // must be constrained to that recording so the remote item is never completed by mistake.
        val f = Fixture()
        f.addReminder(5, recordingId = "rec1")
        val remote = reminderItem("remote", localReminderId = 5, recordingId = "otherDeviceRec")
        val local = reminderItem("local", localReminderId = 5, recordingId = "rec1")
        f.itemDao.items["remote"] = remote
        f.itemDao.items["local"] = local

        val result = f.completer.markDone(5)

        assertTrue(result)
        assertTrue(f.itemDao.items["local"]!!.done, "local reminder's item should be completed")
        assertFalse(f.itemDao.items["remote"]!!.done, "remote item must not be touched")
        assertEquals(listOf(5), f.cancelled)
    }
}
