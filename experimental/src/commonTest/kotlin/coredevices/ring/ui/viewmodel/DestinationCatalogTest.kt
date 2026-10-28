package coredevices.ring.ui.viewmodel

import PlatformUiContext
import com.russhwolf.settings.MapSettings
import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.FakePluginStore
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginActionAgentIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionNoteIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.util.Platform
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DestinationCatalogTest {

    @Test
    fun installedProvidersAreTitledForTheKindAndMarkedConnected() = runTest {
        val index = FakeProvider("index", "Index", storesItemsLocally = true, notesTitle = "Index Notes")
        val notion = FakeProvider("notion", "Notion", supports = setOf(IntegrationSupport.Note), authorized = false)
        val fixture = Fixture(builtIn = listOf(index, notion), pluginsEnabled = false)

        val items = fixture.catalog.items(IntegrationSupport.Note).first()

        assertEquals(
            listOf(
                DestinationItem.Installed(index, "Index Notes", connected = true, hasLockerPage = false),
                DestinationItem.Installed(notion, "Notion", connected = false, hasLockerPage = false),
            ),
            items,
        )
    }

    @Test
    fun refreshPicksUpAProviderThatHasConnected() = runTest {
        val notion = FakeProvider("notion", "Notion", supports = setOf(IntegrationSupport.Note), authorized = false)
        val fixture = Fixture(builtIn = listOf(notion), pluginsEnabled = false)
        assertFalse(fixture.catalog.items(IntegrationSupport.Note).first().single().isConnected)

        notion.authorized = true
        fixture.catalog.refresh()

        assertTrue(fixture.catalog.items(IntegrationSupport.Note).first().single().isConnected)
    }

    @Test
    fun aPluginProviderHasALockerPageOnlyWhileItIsInTheLocker() = runTest {
        val claude = agentPlugin("Claude", "00000000-0000-0000-0002-000000000001")
        val fixture = Fixture(plugins = listOf(claude))

        val before = fixture.catalog.items(IntegrationSupport.Agent).first().single() as DestinationItem.Installed
        assertEquals(PblPluginIntegrationProvider.idFor(claude.pluginUuid), before.id)
        assertTrue(before.connected)
        assertFalse(before.hasLockerPage)

        fixture.lockerUuids.value = listOf(claude.pluginUuid)
        val after = fixture.catalog.items(IntegrationSupport.Agent).first().single() as DestinationItem.Installed
        assertTrue(after.hasLockerPage)
    }

    @Test
    fun storePluginsFollowTheInstalledOnesFilteredByKindAndLocker() = runTest {
        val claude = storePlugin("Claude", "00000000-0000-0000-0002-000000000001", IntegrationSupport.Agent)
        val codex = storePlugin("Codex", "00000000-0000-0000-0002-000000000002", IntegrationSupport.Agent)
        val things = storePlugin("Things", "00000000-0000-0000-0002-000000000003", IntegrationSupport.Note)
        val local = FakeProvider("local", "Local", supports = setOf(IntegrationSupport.Agent), isExternalPlugin = true)
        val fixture = Fixture(builtIn = listOf(local), storePlugins = listOf(claude, codex, things))
        fixture.lockerUuids.value = listOf(codex.pluginUuid)

        assertEquals(
            listOf(local.id, DestinationItem.InStore(claude).id),
            fixture.catalog.items(IntegrationSupport.Agent).first().map { it.id },
        )
        assertEquals(
            listOf(DestinationItem.InStore(things)),
            fixture.catalog.items(IntegrationSupport.Note).first().filterIsInstance<DestinationItem.InStore>(),
        )
        assertEquals("Claude", DestinationItem.InStore(claude).title)
    }

    @Test
    fun withPluginsOffTheStoreIsNotOfferedAndNativeProvidersAre() = runTest {
        val notion = FakeProvider("notion", "Notion", supports = setOf(IntegrationSupport.Note))
        val things = storePlugin("Things", "00000000-0000-0000-0002-000000000003", IntegrationSupport.Note)
        val fixture = Fixture(builtIn = listOf(notion), storePlugins = listOf(things))

        assertEquals(listOf(DestinationItem.InStore(things).id), fixture.catalog.items(IntegrationSupport.Note).first().map { it.id })

        fixture.pluginsEnabled.value = false
        assertEquals(listOf(notion.id), fixture.catalog.items(IntegrationSupport.Note).first().map { it.id })
    }

    @Test
    fun theStoreIsFetchedOnceUnlessTheFetchFailed() = runTest {
        val things = storePlugin("Things", "00000000-0000-0000-0002-000000000003", IntegrationSupport.Note)
        val fixture = Fixture(storePlugins = listOf(things))
        fixture.store.available = false

        assertEquals(emptyList(), fixture.catalog.items(IntegrationSupport.Note).first())
        assertEquals(emptyList(), fixture.catalog.items(IntegrationSupport.Note).first())
        assertEquals(2, fixture.store.fetches)

        fixture.store.available = true
        assertEquals(1, fixture.catalog.items(IntegrationSupport.Note).first().size)
        assertEquals(0, fixture.catalog.items(IntegrationSupport.Reminder).first().size)
        assertEquals(3, fixture.store.fetches)
    }

    @Test
    fun installGoesThroughTheStore() = runTest {
        val things = storePlugin("Things", "00000000-0000-0000-0002-000000000003", IntegrationSupport.Note)
        val fixture = Fixture(storePlugins = listOf(things))

        assertTrue(fixture.catalog.install(things, mapOf("location" to true)))

        assertEquals(listOf(things.pluginUuid to mapOf<String, Boolean?>("location" to true)), fixture.store.installed)
    }

    private class Fixture(
        builtIn: List<IntegrationProvider> = emptyList(),
        plugins: List<FakePlugin> = emptyList(),
        storePlugins: List<StorePlugin> = emptyList(),
        pluginsEnabled: Boolean = true,
    ) {
        val store = FakePluginStore(storePlugins)
        val pluginsEnabled = MutableStateFlow(pluginsEnabled)
        val lockerUuids = MutableStateFlow<List<Uuid>>(emptyList())
        val catalog = DestinationCatalog(
            providers = IntegrationProviderRepository(
                preferences = PreferencesImpl(MapSettings()),
                plugins = FakePlugins(*plugins.toTypedArray()),
                platform = FakePlatform(),
                delegatedItems = DelegatedIntegrationItems(ItemFactory(), ItemRepository(FakeCachedItemDao(), cancelReminder = {})),
                // The repository needs a local provider to fall back to; this one takes nothing.
                builtIn = listOf(FakeProvider("feed", "Feed", storesItemsLocally = true, supports = emptySet())) + builtIn,
            ),
            pluginStore = store,
            pluginsEnabled = this.pluginsEnabled,
            lockerUuids = lockerUuids,
        )
    }

    private fun agentPlugin(name: String, uuid: String) = FakePlugin(
        name = name,
        pluginUuid = Uuid.parse(uuid),
        actions = listOf(ActionDeclaration(name = PblPluginActionAgentIntegration.ACTION_NAME)),
        sources = listOf(
            SourceDeclaration(
                category = PblPluginActionAgentIntegration.CATEGORY,
                items = listOf(PblPluginActionIntegration.CONFIGURED_ITEM),
            ),
        ),
        envelopes = mapOf(
            "${PblPluginActionAgentIntegration.CATEGORY}/${PblPluginActionIntegration.CONFIGURED_ITEM}" to SourceEnvelope(
                pluginUuid = uuid,
                instances = listOf(
                    SourceInstance(
                        instanceId = name,
                        properties = mapOf(
                            PblPluginActionIntegration.CONFIGURED_PROPERTY to
                                mapOf("boolean" to JsonObject(mapOf("value" to JsonPrimitive(true)))),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun storePlugin(name: String, uuid: String, support: IntegrationSupport): StorePlugin {
        val (action, category) = when (support) {
            IntegrationSupport.Agent -> PblPluginActionAgentIntegration.ACTION_NAME to PblPluginActionAgentIntegration.CATEGORY
            IntegrationSupport.Note -> PblPluginActionNoteIntegration.ACTION_NAME to PblPluginActionNoteIntegration.CATEGORY
            IntegrationSupport.Reminder -> error("unused")
        }
        return StorePlugin(
            metadata = PluginMetadata(
                pluginUuid = Uuid.parse(uuid),
                name = name,
                description = "",
                sources = listOf(SourceDeclaration(category = category, items = listOf(PblPluginActionIntegration.CONFIGURED_ITEM))),
                actions = listOf(ActionDeclaration(name = action)),
            ),
            configurable = false,
        )
    }

    private class FakeAgentIntegration : AgentIntegration {
        override suspend fun sendToAgent(message: String, chatId: String?, source: ItemSource?): String = "sent"
        override suspend fun signIn(uiContext: PlatformUiContext) = true
        override suspend fun unlink() {}
        override suspend fun isAuthorized() = true
    }

    private class FakeProvider(
        override val id: String,
        override val title: String,
        override val storesItemsLocally: Boolean = false,
        var authorized: Boolean = true,
        override val supports: Set<IntegrationSupport> = setOf(IntegrationSupport.Note, IntegrationSupport.Agent),
        override val notesTitle: String = title,
        override val isExternalPlugin: Boolean = false,
    ) : IntegrationProvider {
        override suspend fun isAuthorized() = authorized
        override fun agent(): AgentIntegration = FakeAgentIntegration()
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

    private class FakePlatform : Platform {
        override val name = "Android 15"
        override val deviceModelName = "test"
        override suspend fun openUrl(url: String) = Unit
        override suspend fun runWithBgTask(name: String, task: suspend () -> Unit) = task()
    }
}
