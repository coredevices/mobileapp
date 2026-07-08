@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.reminders.ReminderDeepLinkResolver.Companion.FALLBACK_DEEP_LINK
import coredevices.ring.ui.navigation.RingRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class ReminderDeepLinkResolverTest {

    private val reminderId = 7
    private val recordingId = "rec-1"
    private val firestoreId = "item-xyz"

    private fun resolver(reminder: LocalReminderData?, items: List<CachedItem>) =
        ReminderDeepLinkResolver(
            FakeLocalReminderDao(reminder),
            FakeCachedItemDao(items),
        )

    private fun reminderItem(localReminderId: Int?, firestoreId: String = this.firestoreId) =
        CachedItem(
            firestoreId = firestoreId,
            sourceRecordingId = recordingId,
            metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push", localReminderId = localReminderId),
        )

    @Test
    fun fallsBackWhenReminderHasNoRecordingId() = runBlocking {
        val reminder = LocalReminderData(reminderId, time = null, message = "x", recordingId = null)
        assertEquals(FALLBACK_DEEP_LINK, resolver(reminder, emptyList()).resolveDeepLink(reminderId))
    }

    @Test
    fun fallsBackWhenReminderMissing() = runBlocking {
        assertEquals(FALLBACK_DEEP_LINK, resolver(null, emptyList()).resolveDeepLink(reminderId))
    }

    @Test
    fun resolvesObjectDeepLinkForMatchingItem() = runBlocking {
        val reminder = LocalReminderData(reminderId, time = null, message = "x", recordingId = recordingId)
        val items = listOf(reminderItem(localReminderId = reminderId))
        assertEquals(
            RingRoutes.objectDeepLink(firestoreId),
            resolver(reminder, items).resolveDeepLink(reminderId),
        )
    }

    @Test
    fun fallsBackWhenNoItemMatchesLocalReminderId() = runBlocking {
        val reminder = LocalReminderData(reminderId, time = null, message = "x", recordingId = recordingId)
        // An item from the same recording but for a different reminder.
        val items = listOf(reminderItem(localReminderId = 99))
        assertEquals(FALLBACK_DEEP_LINK, resolver(reminder, items).resolveDeepLink(reminderId))
    }
}

private class FakeLocalReminderDao(private val reminder: LocalReminderData?) : LocalReminderDao {
    override suspend fun getReminder(id: Int): LocalReminderData? = reminder?.takeIf { it.id == id }
    override suspend fun insertReminder(reminder: LocalReminderData): Long = error("unused")
    override suspend fun getAllReminders(): List<LocalReminderData> = error("unused")
    override suspend fun getAllRemindersInRange(start: kotlin.time.Instant, end: kotlin.time.Instant): List<LocalReminderData> = error("unused")
    override fun getAllRemindersFlow(): Flow<List<LocalReminderData>> = error("unused")
    override suspend fun setRecordingId(id: Int, recordingId: String) = error("unused")
    override suspend fun clearNotifyBefore(id: Int) = error("unused")

    override suspend fun deleteReminder(id: Int) = error("unused")
}

private class FakeCachedItemDao(private val items: List<CachedItem>) : CachedItemDao {
    override suspend fun getByRecording(recordingId: String): List<CachedItem> =
        items.filter { it.sourceRecordingId == recordingId }
    override suspend fun upsert(item: CachedItem) = error("unused")
    override suspend fun upsertAll(items: List<CachedItem>) = error("unused")
    override suspend fun getById(id: String): CachedItem? = error("unused")
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
