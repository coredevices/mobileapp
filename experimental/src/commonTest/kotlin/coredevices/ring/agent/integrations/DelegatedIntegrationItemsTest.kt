@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.integrations

import PlatformUiContext
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DelegatedIntegrationItemsTest {

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

    private class FakeNoteIntegration(private val result: String?) : NoteIntegration {
        override suspend fun createNote(content: String, source: ItemSource?): String? = result
        override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
        override suspend fun unlink() {}
        override suspend fun isAuthorized(): Boolean = true
    }

    private class FakeReminderIntegration(private val failure: Exception? = null) : ReminderIntegration {
        override suspend fun createReminder(
            title: String,
            deadline: Instant?,
            listId: String?,
            notifyBefore: Duration?,
            source: ItemSource?,
        ): String = failure?.let { throw it } ?: "r1"

        override suspend fun searchForList(listName: String): List<ReminderListEntry> = emptyList()
        override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
        override suspend fun unlink() {}
        override suspend fun isAuthorized(): Boolean = true
    }

    private val now = Clock.System.now()
    private val source = ItemSource(recordingFirestoreId = "rec-1", createdAt = now)

    private fun writer(dao: FakeCachedItemDao) =
        DelegatedIntegrationItems(ItemFactory(), ItemRepository(dao) {})

    @Test
    fun noteDecoratorRecordsDelegatedItemOnSuccess() = runBlocking {
        val dao = FakeCachedItemDao()
        val decorated = DelegatingNoteIntegration(FakeNoteIntegration("n1"), "Notion", writer(dao))

        val id = decorated.createNote("Remember the milk", source)

        assertEquals("n1", id)
        val item = dao.items.values.single().toDocument()
        assertEquals("Remember the milk", item.title)
        assertEquals("rec-1", item.sourceRecordingId)
        assertEquals(now, item.createdAt)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.DelegatedToIntegration)
        assertEquals("Notion", meta.integration)
    }

    @Test
    fun noteDecoratorSkipsItemWhenDelegateCreatesNothing() = runBlocking {
        val dao = FakeCachedItemDao()
        val decorated = DelegatingNoteIntegration(FakeNoteIntegration(null), "Obsidian", writer(dao))

        assertEquals(null, decorated.createNote("Idea", source))
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun reminderDecoratorRecordsDelegatedItem() = runBlocking {
        val dao = FakeCachedItemDao()
        val decorated = DelegatingReminderIntegration(FakeReminderIntegration(), "Google Tasks", writer(dao))

        val id = decorated.createReminder("Call mom", deadline = null, source = source)

        assertEquals("r1", id)
        val item = dao.items.values.single().toDocument()
        assertEquals("Call mom", item.title)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.DelegatedToIntegration)
        assertEquals("Google Tasks", meta.integration)
    }

    @Test
    fun reminderDecoratorPropagatesFailureWithoutItem() = runBlocking {
        val dao = FakeCachedItemDao()
        val decorated = DelegatingReminderIntegration(
            FakeReminderIntegration(failure = Exception("boom")),
            "Google Tasks",
            writer(dao),
        )

        assertFailsWith<Exception> { decorated.createReminder("Call mom", deadline = null, source = source) }
        assertTrue(dao.items.isEmpty())
    }
}
