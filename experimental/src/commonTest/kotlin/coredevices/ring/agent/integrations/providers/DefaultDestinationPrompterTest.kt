package coredevices.ring.agent.integrations.providers

import PlatformUiContext
import com.russhwolf.settings.MapSettings
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.util.Platform
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val PLUGIN_UUID = Uuid.parse("00000000-0000-0000-0002-00000000000e")
private val PLUGIN_ID = PblPluginIntegrationProvider.idFor(PLUGIN_UUID)

private val createNote = ActionDeclaration(name = "create_note")
private val createReminder = ActionDeclaration(name = "create_reminder")

private fun configured(value: Boolean) = SourceEnvelope(
    pluginUuid = "p",
    instances = listOf(
        SourceInstance(
            instanceId = "things",
            properties = mapOf("configured" to mapOf("boolean" to JsonObject(mapOf("value" to JsonPrimitive(value))))),
        )
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDestinationPrompterTest {
    private val index = FakeProvider(
        id = "index",
        supports = setOf(IntegrationSupport.Note, IntegrationSupport.Reminder),
        storesItemsLocally = true,
    )
    private val plugins = FakePlugins()
    private val preferences = PreferencesImpl(MapSettings()).apply { setRingPaired("ring-1") }
    private val repository = IntegrationProviderRepository(
        preferences = preferences,
        plugins = plugins,
        platform = FakePlatform("Android 15"),
        delegatedItems = DelegatedIntegrationItems(
            ItemFactory(),
            ItemRepository(FakeCachedItemDao(), cancelReminder = {}),
        ),
        builtIn = listOf(index),
    )

    private fun installThings(
        notesConfigured: Boolean,
        remindersConfigured: Boolean = false,
        actions: List<ActionDeclaration> = listOf(createNote, createReminder),
    ) = plugins.install(
        FakePlugin(
            actions = actions,
            name = "Things",
            pluginUuid = PLUGIN_UUID,
            sources = listOf(
                SourceDeclaration(category = "notes", items = listOf("configured")),
                SourceDeclaration(category = "reminders", items = listOf("configured")),
            ),
            envelopes = mapOf(
                "notes/configured" to configured(notesConfigured),
                "reminders/configured" to configured(remindersConfigured),
            ),
        )
    )

    private fun TestScope.prompter() = DefaultDestinationPrompter(repository, preferences, this)

    private fun TestScope.closePage(prompter: DefaultDestinationPrompter): DefaultDestinationPrompt? {
        prompter.configPageClosed(PLUGIN_UUID)
        advanceUntilIdle()
        return prompter.prompt.value
    }

    @Test
    fun offersAConfiguredPluginForTheKindsItIsNotYetTheDefaultFor() = runTest {
        installThings(notesConfigured = true, remindersConfigured = false)

        val prompt = assertNotNull(closePage(prompter()))

        assertEquals("Things", prompt.provider.title)
        assertEquals(setOf(IntegrationSupport.Note), prompt.supports)
        assertEquals("notes", prompt.kindsLabel)
        assertEquals(setOf(PLUGIN_ID), preferences.defaultDestinationAsked)
    }

    @Test
    fun offersBothKindsWhenBothAreConfigured() = runTest {
        installThings(notesConfigured = true, remindersConfigured = true)

        val prompt = assertNotNull(closePage(prompter()))

        assertEquals(setOf(IntegrationSupport.Note, IntegrationSupport.Reminder), prompt.supports)
        assertEquals("notes and reminders", prompt.kindsLabel)
    }

    @Test
    fun anUnconfiguredPluginIsNotOfferedAndIsStillAskedAboutOnceConfigured() = runTest {
        installThings(notesConfigured = false)
        val prompter = prompter()

        assertNull(closePage(prompter))
        assertEquals(emptySet<String>(), preferences.defaultDestinationAsked)

        installThings(notesConfigured = true)
        assertNotNull(closePage(prompter))
    }

    @Test
    fun theCurrentDefaultIsNotOffered() = runTest {
        val things = PblPluginIntegrationProvider.forPlugin(installThings(notesConfigured = true, actions = listOf(createNote)), plugins)!!
        repository.selectNoteProvider(things)

        assertNull(closePage(prompter()))
    }

    @Test
    fun askedOnlyOnce() = runTest {
        installThings(notesConfigured = true)
        val prompter = prompter()

        assertNotNull(closePage(prompter))
        prompter.dismiss()

        assertNull(closePage(prompter))
    }

    @Test
    fun acceptingSelectsTheProviderForTheOfferedKinds() = runTest {
        installThings(notesConfigured = true, remindersConfigured = true)
        val prompter = prompter()
        closePage(prompter)

        prompter.accept()

        assertNull(prompter.prompt.value)
        assertEquals(PLUGIN_ID, repository.selectedNoteProvider.value.id)
        assertEquals(PLUGIN_ID, repository.selectedReminderProvider.value.id)
    }

    @Test
    fun acceptingLeavesTheKindsThatWereNotOfferedAlone() = runTest {
        installThings(notesConfigured = true, remindersConfigured = false)
        val prompter = prompter()
        closePage(prompter)

        prompter.accept()

        assertEquals(PLUGIN_ID, repository.selectedNoteProvider.value.id)
        assertSame(index, repository.selectedReminderProvider.value)
    }

    @Test
    fun dismissingKeepsTheCurrentDefaults() = runTest {
        installThings(notesConfigured = true)
        val prompter = prompter()
        closePage(prompter)

        prompter.dismiss()

        assertNull(prompter.prompt.value)
        assertSame(index, repository.selectedNoteProvider.value)
    }

    @Test
    fun aPluginPickedFromASheetIsNotAskedAbout() = runTest {
        installThings(notesConfigured = true)
        val prompter = prompter()
        prompter.pickedFromSheet(PLUGIN_ID)

        assertNull(closePage(prompter))
    }

    @Test
    fun nothingIsOfferedWithoutAPairedIndex() = runTest {
        installThings(notesConfigured = true)
        preferences.setRingPaired(null)

        assertNull(closePage(prompter()))
        assertEquals(emptySet<String>(), preferences.defaultDestinationAsked)
    }

    @Test
    fun aPageForSomethingThatIsNotADestinationIsIgnored() = runTest {
        assertNull(closePage(prompter()))
        assertEquals(emptySet<String>(), preferences.defaultDestinationAsked)
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
    ) : IntegrationProvider {
        override val title = id
        override suspend fun isAuthorized() = true
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
        override suspend fun getAllIncludingDeleted(): List<CachedItem> = emptyList()
        override fun getByListFlow(listId: String): Flow<List<CachedItem>> = flowOf(emptyList())
        override suspend fun getByList(listId: String): List<CachedItem> = emptyList()
        override suspend fun deleteById(id: String) {}
        override suspend fun deleteAll() {}
        override suspend fun getAllIds(): List<String> = emptyList()
        override suspend fun countLocked(): Int = 0
    }
}
