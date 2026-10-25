package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.compose.runtime.Composable
import com.russhwolf.settings.MapSettings
import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.indexai.data.entity.mcp.DisabledBuiltinMcp
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.indexai.database.dao.DisabledBuiltinMcpDao
import coredevices.indexai.database.dao.HttpMcpServerDao
import coredevices.mcp.client.McpIntegration
import coredevices.ring.agent.DefaultCaptureType
import coredevices.ring.agent.IndexActionsRepository
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PluginStore
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.McpServerRepository
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.util.Platform
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class IndexActionsViewModelTest {

    @Test
    fun agentReachableFollowsTheGestureRoutes() = runMainTest {
        val fixture = Fixture()
        fixture.routing.setRoutes(
            mapOf(
                RingGesture.Hold to GestureDestination.WebSearch,
                RingGesture.ClickHold to GestureDestination.WebhookOnly,
            )
        )
        assertFalse(fixture.viewModel.agentReachable.value)

        fixture.routing.setRoute(RingGesture.Hold, GestureDestination.IndexAgent)
        assertTrue(fixture.viewModel.agentReachable.value)
    }

    @Test
    fun captureTypeRoundTripsThroughPreferences() = runMainTest {
        val fixture = Fixture()

        fixture.viewModel.setCaptureType(DefaultCaptureType.Reminder)

        assertEquals(DefaultCaptureType.Reminder, fixture.viewModel.captureType.value)
        assertEquals(DefaultCaptureType.Reminder, fixture.preferences.defaultCaptureType.value)
    }

    @Test
    fun pendingNoteConnectSelectsTheProviderOnceItIsAvailableThenClears() = runMainTest {
        val fixture = Fixture()
        backgroundScope.launch { fixture.viewModel.availableNoteProviders.collect {} }
        fixture.notion.authorized = false

        fixture.viewModel.noteConnectStarted(fixture.notion.id)
        fixture.viewModel.refreshAvailableProviders()
        advanceUntilIdle()
        assertSame(fixture.index, fixture.repository.selectedNoteProvider.value)

        fixture.notion.authorized = true
        fixture.viewModel.refreshAvailableProviders()
        advanceUntilIdle()
        assertSame(fixture.notion, fixture.repository.selectedNoteProvider.value)

        // Consumed: a later refresh must not re-apply it over a different choice.
        fixture.repository.selectNoteProvider(fixture.index)
        fixture.viewModel.refreshAvailableProviders()
        advanceUntilIdle()
        assertSame(fixture.index, fixture.repository.selectedNoteProvider.value)
    }

    @Test
    fun pendingReminderConnectSelectsTheProviderOnceItIsAvailable() = runMainTest {
        val fixture = Fixture()
        backgroundScope.launch { fixture.viewModel.availableReminderProviders.collect {} }
        fixture.googleTasks.authorized = false

        fixture.viewModel.reminderConnectStarted(fixture.googleTasks.id)
        fixture.googleTasks.authorized = true
        fixture.viewModel.refreshAvailableProviders()
        advanceUntilIdle()

        assertSame(fixture.googleTasks, fixture.repository.selectedReminderProvider.value)
    }

    @Test
    fun selectingAProviderCancelsAPendingConnect() = runMainTest {
        val fixture = Fixture()
        backgroundScope.launch { fixture.viewModel.availableNoteProviders.collect {} }
        fixture.notion.authorized = false

        fixture.viewModel.noteConnectStarted(fixture.notion.id)
        fixture.viewModel.selectNoteProvider(fixture.index)
        fixture.notion.authorized = true
        fixture.viewModel.refreshAvailableProviders()
        advanceUntilIdle()

        assertSame(fixture.index, fixture.repository.selectedNoteProvider.value)
    }

    @Test
    fun storePluginsExcludeInstalledOnesAndAreEmptyWhilePluginsAreOff() = runMainTest {
        val things = storePlugin("Things", "00000000-0000-0000-0002-000000000001")
        val weather = storePlugin("Weather", "00000000-0000-0000-0002-000000000002")
        val fixture = Fixture(storePlugins = listOf(things, weather))
        backgroundScope.launch { fixture.viewModel.storePlugins.collect {} }

        fixture.pluginsEnabled.value = true
        fixture.lockerUuids.value = listOf(things.pluginUuid)
        advanceUntilIdle()
        assertEquals(listOf(weather), fixture.viewModel.storePlugins.value)

        fixture.pluginsEnabled.value = false
        advanceUntilIdle()
        assertEquals(emptyList(), fixture.viewModel.storePlugins.value)
    }

    private fun runMainTest(block: suspend TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) { block() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun storePlugin(name: String, uuid: String) = StorePlugin(
        metadata = PluginMetadata(
            pluginUuid = Uuid.parse(uuid),
            name = name,
            description = "",
            sources = emptyList(),
            actions = emptyList(),
        ),
        configurable = false,
    )

    private class Fixture(storePlugins: List<StorePlugin> = emptyList()) {
        val index = FakeProvider(
            id = "index",
            supports = setOf(IntegrationSupport.Note, IntegrationSupport.Reminder),
            storesItemsLocally = true,
        )
        val notion = FakeProvider(id = "notion", supports = setOf(IntegrationSupport.Note))
        val googleTasks = FakeProvider(id = "google_tasks", supports = setOf(IntegrationSupport.Reminder))
        val preferences = PreferencesImpl(MapSettings())
        val routing = GestureRoutingPreferences(MapSettings(), preferences)
        val repository = IntegrationProviderRepository(
            preferences = preferences,
            plugins = FakePlugins(),
            platform = FakePlatform("Android 15"),
            delegatedItems = DelegatedIntegrationItems(
                ItemFactory(),
                ItemRepository(FakeCachedItemDao(), cancelReminder = {}),
            ),
            builtIn = listOf(index, notion, googleTasks),
        )
        val pluginsEnabled = MutableStateFlow(false)
        val lockerUuids = MutableStateFlow<List<Uuid>>(emptyList())
        val viewModel = IndexActionsViewModel(
            indexActionsRepository = IndexActionsRepository(
                servletRepository = FakeServletRepository(),
                disabledBuiltins = { flowOf(emptySet<String>()) },
                setBuiltinEnabled = { _, _ -> },
                localAgentOnly = flowOf(false),
                calendarConnected = flowOf(true),
                beeperUnavailable = flowOf<String?>(null),
                installedPlugins = { emptyList() },
                disabledPlugins = { flowOf(emptySet<Uuid>()) },
                persistPluginGrantEnabled = { _, _, _ -> },
                watchPaired = flowOf(true),
            ),
            mcpServerRepository = McpServerRepository(
                FakeDisabledBuiltinDao(),
                FakeHttpServerDao(),
                FakeServletRepository(),
            ),
            pluginStore = FakePluginStore(storePlugins),
            providers = repository,
            preferences = preferences,
            gestureRouting = routing,
            platform = FakePlatform("Android 15"),
            pluginsEnabled = pluginsEnabled,
            lockerUuids = lockerUuids,
        )
    }

    private class FakePluginStore(private val plugins: List<StorePlugin>) : PluginStore {
        override suspend fun indexPlugins(): List<StorePlugin>? = plugins
        override suspend fun reviewInstall(pluginUuid: Uuid): InstallReview? = null
        override suspend fun install(pluginUuid: Uuid, granted: Map<String, Boolean?>): Boolean = true

        @Composable
        override fun ReviewInstallDialog(review: InstallReview, onAnswer: (Map<String, Boolean?>?) -> Unit) {
        }
    }

    private class FakeServletRepository : ServletRepository {
        override fun getAllServlets(): List<McpServerDefinition> = emptyList()
        override fun resolveName(name: String): McpIntegration? = null
    }

    private class FakeDisabledBuiltinDao : DisabledBuiltinMcpDao {
        override fun getAllFlow(): Flow<List<DisabledBuiltinMcp>> = flowOf(emptyList())
        override suspend fun insert(disabled: DisabledBuiltinMcp) = Unit
        override suspend fun delete(disabled: DisabledBuiltinMcp) = Unit
    }

    private class FakeHttpServerDao : HttpMcpServerDao {
        override suspend fun insertServer(server: HttpMcpServerEntity): Long = 0L
        override suspend fun deleteServer(server: HttpMcpServerEntity) = Unit
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
        override fun getAllFlow(): Flow<List<HttpMcpServerEntity>> = flowOf(emptyList())
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
        var authorized: Boolean = true,
    ) : IntegrationProvider {
        override val title = id
        override suspend fun isAuthorized() = authorized
        override fun notes(): NoteIntegration = FakeNoteIntegration()
        override fun reminders(): ReminderIntegration = FakeReminderIntegration()
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
}
