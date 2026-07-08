@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.dao.CachedListDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_SHOPPING_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.service.indexfeed.ItemFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class BuiltInReminderFeedItemsTest {

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
        override suspend fun countLocked(): Int {
            return 0
        }
    }

    private class FakeCachedListDao(val lists: List<CachedList>) : CachedListDao {
        override suspend fun upsert(list: CachedList) = error("unused")
        override suspend fun upsertAll(lists: List<CachedList>) = error("unused")
        override suspend fun getById(id: String): CachedList? = lists.firstOrNull { it.firestoreId == id }
        override fun getByIdFlow(id: String): Flow<CachedList?> = flowOf(lists.firstOrNull { it.firestoreId == id })
        override fun getAllFlow(): Flow<List<CachedList>> = flowOf(lists)
        override fun getAllForSyncFlow(): Flow<List<CachedList>> = flowOf(lists)
        override suspend fun getBySeed(seed: String): CachedList? = lists.firstOrNull { it.seed == seed }
        override suspend fun count(): Int = lists.size
        override suspend fun deleteById(id: String) = error("unused")
        override suspend fun deleteAll() = error("unused")
        override suspend fun countLocked(): Int {
            return 0
        }
    }

    private val defaultLists = listOf(
        CachedList(firestoreId = LIST_NOTES_SELF_ID, title = "Notes to self", seed = "notes_self"),
        CachedList(firestoreId = LIST_TODOS_ID, title = "Reminders", seed = "todos"),
        CachedList(firestoreId = LIST_SHOPPING_ID, title = "Shopping", seed = "shopping"),
    )

    private val now = Clock.System.now()

    private fun fixture(lists: List<CachedList> = defaultLists): Pair<BuiltInReminderFeedItems, FakeCachedItemDao> {
        val itemDao = FakeCachedItemDao()
        val feedItems = BuiltInReminderFeedItems(
            ItemFactory(),
            ItemRepository(itemDao) {},
            ListRepository(FakeCachedListDao(lists)),
        )
        return feedItems to itemDao
    }

    @Test
    fun createFeedItemWithListIdMakesNoteInThatList() = runBlocking {
        val (feedItems, itemDao) = fixture()
        feedItems.createFeedItem(
            localReminderId = 5,
            title = "Umbrella",
            deadline = null,
            listId = "list_custom",
            notifyBefore = null,
            source = ItemSource(recordingFirestoreId = "rec-1", createdAt = now),
        )
        val item = itemDao.items.values.single().toDocument()
        assertEquals("Umbrella", item.title)
        assertEquals(listOf("list_custom"), item.parentListIds)
        assertEquals("rec-1", item.sourceRecordingId)
        assertEquals(now, item.createdAt)
        assertNull(item.sourceToolCallId)
        assertTrue(item.metadata is ItemMetadata.Note)
    }

    @Test
    fun createFeedItemWithoutListIdMakesReminderInTodos() = runBlocking {
        val (feedItems, itemDao) = fixture()
        val deadline = now + 5.minutes
        feedItems.createFeedItem(
            localReminderId = 42,
            title = "Call mom",
            deadline = deadline,
            listId = null,
            notifyBefore = 2.hours,
            source = ItemSource(recordingFirestoreId = "rec-1", createdAt = now),
        )
        val item = itemDao.items.values.single().toDocument()
        assertEquals("Call mom", item.title)
        assertEquals(deadline, item.dueAt)
        assertEquals(listOf(LIST_TODOS_ID), item.parentListIds)
        assertEquals("rec-1", item.sourceRecordingId)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Reminder)
        assertEquals(42, meta.localReminderId)
        assertEquals(2.hours.inWholeMilliseconds, meta.notifyBeforeMillis)
    }

    @Test
    fun createFeedItemWithoutSourceStillPersistsUnlinkedItem() = runBlocking {
        val (feedItems, itemDao) = fixture()
        feedItems.createFeedItem(
            localReminderId = 1,
            title = "Call mom",
            deadline = null,
            listId = null,
            notifyBefore = null,
            source = null,
        )
        val item = itemDao.items.values.single().toDocument()
        assertNull(item.sourceRecordingId)
        assertTrue(item.metadata is ItemMetadata.Reminder)
    }

    @Test
    fun searchForListMatchesByTitle() = runBlocking {
        val (feedItems, _) = fixture()
        val entry = feedItems.searchForList("shopping").single()
        assertEquals(LIST_SHOPPING_ID, entry.id)
        assertEquals("Shopping", entry.title)
    }

    @Test
    fun searchForListFallsBackToSeedAfterRename() = runBlocking {
        // "Reminders" (renamed from "Todos") no longer title-matches 'todo', but its seed does.
        val (feedItems, _) = fixture()
        val entry = feedItems.searchForList("todo").single()
        assertEquals(LIST_TODOS_ID, entry.id)
        assertEquals("Reminders", entry.title)
    }

    @Test
    fun searchForListFallsBackToNotesListWhenNothingMatches() = runBlocking {
        val (feedItems, _) = fixture()
        val entry = feedItems.searchForList("groceries").single()
        assertEquals(LIST_NOTES_SELF_ID, entry.id)
        assertEquals("Notes to self", entry.title)
    }
}
