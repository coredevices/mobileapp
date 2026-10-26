package coredevices.ring.agent.integrations.providers

import PlatformUiContext
import com.russhwolf.settings.MapSettings
import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.DelegatingAgentIntegration
import coredevices.ring.agent.integrations.DelegatingNoteIntegration
import coredevices.ring.agent.integrations.DelegatingReminderIntegration
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.util.Platform
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.Plugin
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Instant

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

private class FakeAgentIntegration : AgentIntegration {
    override suspend fun sendToAgent(message: String, chatId: String?, source: ItemSource?): String = "sent"
    override suspend fun signIn(uiContext: PlatformUiContext) = true
    override suspend fun unlink() {}
    override suspend fun isAuthorized() = true
}

private class FakeProvider(
    override val id: String,
    override val supports: Set<IntegrationSupport>,
    override val platforms: Set<SupportedPlatform> = SupportedPlatform.entries.toSet(),
    override val storesItemsLocally: Boolean = false,
    var authorized: Boolean = true,
    override val title: String = id,
) : IntegrationProvider {
    val notes = FakeNoteIntegration()
    val reminders = FakeReminderIntegration()
    val agent = FakeAgentIntegration()
    override suspend fun isAuthorized() = authorized
    override fun notes(): NoteIntegration = notes
    override fun reminders(): ReminderIntegration = reminders
    override fun agent(): AgentIntegration = agent
}

private class FakePlatform(override val name: String) : Platform {
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
    override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
    override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
    override suspend fun deleteById(id: String) {}
    override suspend fun deleteAll() {}
    override suspend fun getAllIds(): List<String> = emptyList()
    override suspend fun countLocked(): Int = 0
}

class IntegrationProviderRepositoryTest {
    private val index = FakeProvider(
        id = "index",
        supports = setOf(IntegrationSupport.Note, IntegrationSupport.Reminder),
        storesItemsLocally = true,
    )
    private val notion = FakeProvider(id = "notion", supports = setOf(IntegrationSupport.Note))
    private val googleTasks = FakeProvider(
        id = "google_tasks",
        supports = setOf(IntegrationSupport.Reminder),
        title = "Google Tasks",
    )
    private val tasker = FakeProvider(
        id = "tasker",
        supports = setOf(IntegrationSupport.Note, IntegrationSupport.Reminder),
        platforms = setOf(SupportedPlatform.Android),
    )
    private val iosReminders = FakeProvider(
        id = "ios_reminders",
        supports = setOf(IntegrationSupport.Reminder),
        platforms = setOf(SupportedPlatform.Ios),
    )
    private val builtIn = listOf(index, notion, googleTasks, tasker, iosReminders)
    private val preferences = PreferencesImpl(MapSettings())

    private fun repository(
        platform: String = "Android",
        plugins: List<Plugin> = emptyList(),
    ) = IntegrationProviderRepository(
        preferences = preferences,
        plugins = FakePlugins(*plugins.toTypedArray()),
        platform = FakePlatform(platform),
        delegatedItems = DelegatedIntegrationItems(ItemFactory(), ItemRepository(FakeCachedItemDao(), cancelReminder = {})),
        builtIn = builtIn,
    )

    @Test
    fun providersAreFilteredByPlatform() {
        assertEquals(listOf(index, notion, googleTasks, tasker), repository("Android").allProviders())
        assertEquals(listOf(index, notion, googleTasks, iosReminders), repository("iOS").allProviders())
    }

    @Test
    fun providersAreFilteredByWhatTheySupport() {
        val repository = repository()

        assertEquals(listOf(index, notion, tasker), repository.providers(IntegrationSupport.Note))
        assertEquals(listOf(index, googleTasks, tasker), repository.providers(IntegrationSupport.Reminder))
    }

    @Test
    fun availableProvidersAreTheConnectedOnes() = runTest {
        notion.authorized = false
        tasker.authorized = false

        assertEquals(listOf(index), repository().availableProviders(IntegrationSupport.Note))
        assertEquals(listOf(index, googleTasks), repository().availableProviders(IntegrationSupport.Reminder))
    }

    @Test
    fun aPluginIsAvailableForEachKindItIsConfiguredFor() = runTest {
        fun configured(value: Boolean) = SourceEnvelope(
            pluginUuid = "p",
            instances = listOf(
                SourceInstance(
                    instanceId = "things",
                    properties = mapOf("configured" to mapOf("boolean" to JsonObject(mapOf("value" to JsonPrimitive(value))))),
                )
            ),
        )
        val plugin = FakePlugin(
            actions = listOf(ActionDeclaration(name = "create_note"), ActionDeclaration(name = "create_reminder")),
            name = "Things",
            sources = listOf(
                SourceDeclaration(category = "notes", items = listOf("configured")),
                SourceDeclaration(category = "reminders", items = listOf("configured")),
            ),
            envelopes = mapOf("notes/configured" to configured(true), "reminders/configured" to configured(false)),
        )
        val repository = repository(plugins = listOf(plugin))
        val id = PblPluginIntegrationProvider.idFor(plugin.pluginUuid)

        assertEquals(id, repository.availableProviders(IntegrationSupport.Note).last().id)
        assertNull(repository.availableProviders(IntegrationSupport.Reminder).find { it.id == id })
    }

    @Test
    fun pluginsWithMatchingActionsAreOfferedAsProviders() {
        val plugin = FakePlugin(
            actions = listOf(ActionDeclaration(name = "create_reminder")),
            name = "Things",
            sources = listOf(SourceDeclaration(category = "reminders", items = listOf("configured"))),
        )

        val providers = repository(plugins = listOf(plugin)).providers(IntegrationSupport.Reminder)

        val pluginProvider = assertIs<PblPluginIntegrationProvider>(providers.last())
        assertEquals("Things", pluginProvider.title)
        assertEquals(PblPluginIntegrationProvider.idFor(plugin.pluginUuid), pluginProvider.id)
        assertNull(repository(plugins = listOf(plugin)).providers(IntegrationSupport.Note).find { it.id == pluginProvider.id })
    }

    @Test
    fun selectionIsPersistedAndResolvedById() {
        val repository = repository()

        repository.selectNoteProvider(notion)
        repository.selectReminderProvider(googleTasks)

        assertEquals("notion", preferences.noteProviderId.value)
        assertEquals("google_tasks", preferences.reminderProviderId.value)
        assertSame(notion, repository.selectedNoteProvider.value)
        assertSame(googleTasks, repository.selectedReminderProvider.value)
    }

    @Test
    fun selectingAProviderThatCannotHandleTheKindIsRejected() {
        val repository = repository()

        assertFailsWith<IllegalArgumentException> { repository.selectNoteProvider(googleTasks) }
        assertFailsWith<IllegalArgumentException> { repository.selectReminderProvider(notion) }
    }

    @Test
    fun unknownOrUnsupportedSelectionsFallBackToTheLocalProvider() {
        preferences.setNoteProviderId("plugin:gone")
        preferences.setReminderProviderId("notion")

        val repository = repository()

        assertSame(index, repository.selectedNoteProvider.value)
        assertSame(index, repository.selectedReminderProvider.value)
    }

    @Test
    fun localProviderIntegrationsAreHandedOutUnwrapped() {
        val repository = repository()

        assertSame(index.notes, repository.noteIntegration(index))
        assertSame(index.reminders, repository.reminderIntegration(index))
    }

    @Test
    fun externalProviderIntegrationsRecordDelegatedItems() {
        val repository = repository()

        assertIs<DelegatingNoteIntegration>(repository.noteIntegration(notion))
        assertIs<DelegatingReminderIntegration>(repository.reminderIntegration(googleTasks))
    }

    @Test
    fun agentIntegrationsRecordDelegatedItemsAndOnlyAgentProvidersResolveForGestures() {
        val claude = FakeProvider("plugin:claude", setOf(IntegrationSupport.Agent))
        val repository = IntegrationProviderRepository(
            preferences = preferences,
            plugins = FakePlugins(),
            platform = FakePlatform("Android"),
            delegatedItems = DelegatedIntegrationItems(ItemFactory(), ItemRepository(FakeCachedItemDao(), cancelReminder = {})),
            builtIn = builtIn + claude,
        )

        assertIs<DelegatingAgentIntegration>(repository.agentIntegration(claude))
        assertSame(claude, repository.agentProvider(claude.id))
        assertNull(repository.agentProvider(notion.id))
        assertNull(repository.agentProvider("plugin:gone"))
        assertSame(claude, repository.destinationProvider(null, IntegrationSupport.Agent))
    }

    @Test
    fun integrationsDefaultToTheSelectedProvider() {
        val repository = repository()
        repository.selectNoteProvider(notion)

        assertIs<DelegatingNoteIntegration>(repository.noteIntegration())
        assertSame(index.reminders, repository.reminderIntegration())
    }

    @Test
    fun providersAreFoundByNameForTheKindTheySupport() {
        val repository = repository()

        assertSame(googleTasks, repository.findProviderByName("google tasks", IntegrationSupport.Reminder))
        assertSame(googleTasks, repository.findProviderByName("GoogleTasks", IntegrationSupport.Reminder))
        assertNull(repository.findProviderByName("Google Tasks", IntegrationSupport.Note))
        assertNull(repository.findProviderByName("TickTick", IntegrationSupport.Reminder))
    }

    @Test
    fun destinationFallsBackToTheSelectionOnlyWhenUnnamed() {
        val repository = repository()
        repository.selectNoteProvider(notion)

        assertSame(notion, repository.destinationProvider(null, IntegrationSupport.Note))
        assertSame(index, repository.destinationProvider(null, IntegrationSupport.Reminder))
        assertSame(googleTasks, repository.destinationProvider("google tasks", IntegrationSupport.Reminder))
        assertNull(repository.destinationProvider("TickTick", IntegrationSupport.Reminder))
    }

    @Test
    fun mentionedProvidersAreTheConnectedOnesNamedInTheTextOtherThanTheSelection() = runTest {
        val repository = repository()
        val text = "set a reminder on google tasks and tasker to buy milk"

        assertEquals(listOf(googleTasks, tasker), repository.mentionedProviders(text, IntegrationSupport.Reminder))

        tasker.authorized = false
        assertEquals(listOf(googleTasks), repository.mentionedProviders(text, IntegrationSupport.Reminder))

        repository.selectReminderProvider(googleTasks)
        assertEquals(emptyList(), repository.mentionedProviders(text, IntegrationSupport.Reminder))
        assertEquals(emptyList(), repository.mentionedProviders("remind me to buy milk", IntegrationSupport.Reminder))
    }

    @Test
    fun shortcutTitlesComeFromTheProvider() {
        val repository = repository()

        assertNull(repository.shortcutDestinationTitle(NoteShortcutType.SendToMe))
        assertEquals("notion", repository.shortcutDestinationTitle(NoteShortcutType.SendToNoteProvider("notion")))
        assertEquals("Google Tasks", repository.shortcutDestinationTitle(NoteShortcutType.SendToReminderProvider("google_tasks")))
        assertEquals("Unavailable provider", repository.shortcutDestinationTitle(NoteShortcutType.SendToNoteProvider("plugin:gone")))
    }
}
