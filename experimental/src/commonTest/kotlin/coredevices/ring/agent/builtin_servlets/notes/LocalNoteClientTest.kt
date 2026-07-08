@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.builtin_servlets.notes

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.ItemFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class LocalNoteClientTest {

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

    private fun fixture(): Pair<LocalNoteClient, FakeCachedItemDao> {
        val itemDao = FakeCachedItemDao()
        return LocalNoteClient(ItemFactory(), ItemRepository(itemDao) {}) to itemDao
    }

    @Test
    fun createNotePersistsNoteInNotesListAndReturnsItsId() = runBlocking {
        val (client, itemDao) = fixture()
        val createdAt = Clock.System.now()

        val id = client.createNote("Remember the milk", ItemSource(recordingFirestoreId = "rec-1", createdAt = createdAt))

        val item = itemDao.items.getValue(id).toDocument()
        assertEquals("Remember the milk", item.title)
        assertEquals(listOf(LIST_NOTES_SELF_ID), item.parentListIds)
        assertEquals("rec-1", item.sourceRecordingId)
        assertEquals(createdAt, item.createdAt)
        assertNull(item.sourceToolCallId)
        assertTrue(item.metadata is ItemMetadata.Note)
    }

    @Test
    fun createNoteWithoutSourceLeavesRecordingUnlinked() = runBlocking {
        val (client, itemDao) = fixture()
        val id = client.createNote("Idea")
        assertNull(itemDao.items.getValue(id).toDocument().sourceRecordingId)
    }
}
