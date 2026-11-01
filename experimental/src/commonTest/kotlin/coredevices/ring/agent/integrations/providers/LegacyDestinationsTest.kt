package coredevices.ring.agent.integrations.providers

import PlatformUiContext
import com.russhwolf.settings.MapSettings
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.util.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class LegacyDestinationsTest {
    private val index = FakeProvider(
        id = "index",
        supports = setOf(IntegrationSupport.Note, IntegrationSupport.Reminder),
        storesItemsLocally = true,
    )
    private val notion = FakeProvider(id = "notion", supports = setOf(IntegrationSupport.Note), title = "Notion")
    private val googleTasks = FakeProvider(id = "google_tasks", supports = setOf(IntegrationSupport.Reminder))
    private val tasker = FakeProvider(id = "tasker", supports = setOf(IntegrationSupport.Note, IntegrationSupport.Reminder))
    private val plugin = FakeProvider(
        id = "plugin:1",
        supports = setOf(IntegrationSupport.Note),
        isExternalPlugin = true,
    )
    private val preferences = PreferencesImpl(MapSettings())
    private val repository = IntegrationProviderRepository(
        preferences = preferences,
        plugins = FakePlugins(),
        platform = FakePlatform(),
        delegatedItems = DelegatedIntegrationItems(ItemFactory(), ItemRepository(FakeCachedItemDao(), cancelReminder = {})),
        builtIn = listOf(index, notion, googleTasks, tasker, plugin),
    )
    private val pluginsEnabled = MutableStateFlow(true)
    private val legacy = LegacyDestinations(repository, preferences, pluginsEnabled)

    @Test
    fun nothingIsPendingWhilePluginsAreOff() = runTest {
        pluginsEnabled.value = false
        repository.selectNoteProvider(notion)

        assertEquals(emptyList<LegacyDestination>(), legacy.pending.first())
        assertFalse(legacy.isLegacy(notion))
    }

    @Test
    fun localAndPluginDestinationsAreNotPending() = runTest {
        repository.selectNoteProvider(plugin)
        repository.selectReminderProvider(index)

        assertEquals(emptyList<LegacyDestination>(), legacy.pending.first())
        assertEquals(0, legacy.count.first())
    }

    @Test
    fun hiddenNativeDestinationsArePendingPerSetting() = runTest {
        repository.selectNoteProvider(tasker)
        repository.selectReminderProvider(tasker)
        preferences.setNoteShortcut(NoteShortcutType.SendToNoteProvider(notion.id))

        assertEquals(
            listOf(
                LegacyDestination(tasker, LegacyDestination.Setting.Notes),
                LegacyDestination(tasker, LegacyDestination.Setting.Reminders),
                LegacyDestination(notion, LegacyDestination.Setting.Shortcut),
            ),
            legacy.pending.first(),
        )
        assertEquals(3, legacy.count.first())
    }

    @Test
    fun shortcutToAnUnknownOrLocalProviderIsNotPending() = runTest {
        preferences.setNoteShortcut(NoteShortcutType.SendToReminderProvider("gone"))
        assertEquals(emptyList<LegacyDestination>(), legacy.pending.first())

        preferences.setNoteShortcut(NoteShortcutType.SendToNoteProvider(index.id))
        assertEquals(emptyList<LegacyDestination>(), legacy.pending.first())
    }

    @Test
    fun onlyHiddenNativeUsesAreReportedAsLegacy() = runTest {
        val used = mutableListOf<IntegrationProvider>()
        backgroundScope.launch { legacy.legacyUse.collect { used += it } }
        runCurrent()

        repository.noteIntegration(index)
        repository.noteIntegration(plugin)
        repository.noteIntegration(notion)
        repository.reminderIntegration(googleTasks)
        runCurrent()

        assertEquals(listOf<IntegrationProvider>(notion, googleTasks), used)
        assertTrue(legacy.isLegacy(notion))
    }

    private class FakeNoteIntegration : NoteIntegration {
        override suspend fun createNote(content: String, source: ItemSource?): String = "n1"
        override suspend fun signIn(uiContext: PlatformUiContext) = true
        override suspend fun unlink() {}
        override suspend fun isAuthorized() = true
    }

    private class FakeReminderIntegration : ReminderIntegration {
        override suspend fun createReminder(
            title: String,
            deadline: Instant?,
            listId: String?,
            notifyBefore: Duration?,
            source: ItemSource?,
        ): String = "r1"

        override suspend fun searchForList(listName: String): List<ReminderListEntry> = emptyList()
        override suspend fun signIn(uiContext: PlatformUiContext) = true
        override suspend fun unlink() {}
        override suspend fun isAuthorized() = true
    }

    private class FakeProvider(
        override val id: String,
        override val supports: Set<IntegrationSupport>,
        override val storesItemsLocally: Boolean = false,
        override val isExternalPlugin: Boolean = false,
        override val title: String = id,
    ) : IntegrationProvider {
        override fun notes(): NoteIntegration = FakeNoteIntegration()
        override fun reminders(): ReminderIntegration = FakeReminderIntegration()
    }

    private class FakePlatform : Platform {
        override val name = "Android"
        override val deviceModelName = "test"
        override suspend fun openUrl(url: String) {}
        override suspend fun runWithBgTask(name: String, task: suspend () -> Unit) = task()
    }

    private class FakeCachedItemDao : CachedItemDao {
        override suspend fun upsert(item: CachedItem) {}
        override suspend fun upsertAll(items: List<CachedItem>) {}
        override suspend fun getById(id: String): CachedItem? = null
        override fun getByIdFlow(id: String): Flow<CachedItem?> = flowOf(null)
        override fun getAllFlow(): Flow<List<CachedItem>> = flowOf(emptyList())
        override fun getAllForSyncFlow(): Flow<List<CachedItem>> = flowOf(emptyList())
        override fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByRecording(recordingId: String): List<CachedItem> = emptyList()
        override suspend fun getAllActive(): List<CachedItem> = emptyList()
        override suspend fun getAllIncludingDeleted(): List<CachedItem> = emptyList()
        override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
        override suspend fun deleteById(id: String) {}
        override suspend fun deleteAll() {}
        override suspend fun getAllIds(): List<String> = emptyList()
        override suspend fun countLocked(): Int = 0
    }
}
