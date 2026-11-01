@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.reminders.ReminderDeepLinkResolver.Companion.FALLBACK_DEEP_LINK
import coredevices.ring.ui.navigation.RingRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class ReminderDeepLinkResolverTest {

    private val itemId = "item-xyz"

    private fun resolver(items: List<CachedItem>) = ReminderDeepLinkResolver(FakeCachedItemDao(items))

    private fun reminderItem(firestoreId: String) = CachedItem(
        firestoreId = firestoreId,
        metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push"),
    )

    @Test
    fun resolvesObjectDeepLinkForItemPresentLocally() = runBlocking {
        assertEquals(
            RingRoutes.objectDeepLink(itemId),
            resolver(listOf(reminderItem(itemId))).resolveDeepLink(itemId),
        )
    }

    @Test
    fun fallsBackWhenItemNotSyncedHere() = runBlocking {
        assertEquals(FALLBACK_DEEP_LINK, resolver(emptyList()).resolveDeepLink(itemId))
    }
}

private class FakeCachedItemDao(private val items: List<CachedItem>) : CachedItemDao {
    override suspend fun getById(id: String): CachedItem? = items.firstOrNull { it.firestoreId == id }
    override suspend fun getByRecording(recordingId: String): List<CachedItem> = error("unused")
    override suspend fun getAllActive(): List<CachedItem> = error("unused")
    override suspend fun getAllIncludingDeleted(): List<CachedItem> = error("unused")
    override suspend fun upsert(item: CachedItem) = error("unused")
    override suspend fun upsertAll(items: List<CachedItem>) = error("unused")
    override fun getByIdFlow(id: String): Flow<CachedItem?> = error("unused")
    override fun getAllFlow(): Flow<List<CachedItem>> = error("unused")
    override fun getAllForSyncFlow(): Flow<List<CachedItem>> = error("unused")
    override fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>> = error("unused")
    override fun getByListFlow(listId: String): Flow<List<CachedItem>> = error("unused")
    override suspend fun getByList(listId: String): List<CachedItem> = error("unused")
    override suspend fun deleteById(id: String) = error("unused")
    override suspend fun deleteAll() = error("unused")
    override suspend fun getAllIds(): List<String> = error("unused")
    override suspend fun countLocked(): Int = error("unused")
}
