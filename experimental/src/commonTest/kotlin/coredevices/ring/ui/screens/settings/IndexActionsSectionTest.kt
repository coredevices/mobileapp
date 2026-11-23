package coredevices.ring.ui.screens.settings

import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_actions_capture_type_note
import coreapp.ring.generated.resources.settings_actions_capture_type_reminder
import coreapp.ring.generated.resources.settings_destination_hold_to_reconfigure
import coreapp.ring.generated.resources.settings_destination_not_connected
import coreapp.ring.generated.resources.settings_actions_grant_actions
import coreapp.ring.generated.resources.settings_actions_grant_sources
import coreapp.ring.generated.resources.settings_actions_plugin_actions_count
import coreapp.ring.generated.resources.settings_actions_plugin_sources_count
import coredevices.ring.agent.DefaultCaptureType
import androidx.compose.ui.state.ToggleableState
import coredevices.ring.agent.IndexAction
import coredevices.ring.agent.IndexPlugin
import coredevices.ring.agent.PluginGrantState
import coredevices.ring.agent.LOCAL_MODEL_REASON
import coredevices.ring.agent.NOT_CONNECTED_REASON
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders.GoogleTasks
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders.Index
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders.IosReminders
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders.Notion
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders.Obsidian
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders.Tasker
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.visibleDestinations
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.FakePlugins
import coredevices.ring.agent.pebble_plugin.PblPluginActionAgentIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionNoteIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionReminderIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.ui.viewmodel.DestinationItem
import coredevices.ring.ui.viewmodel.isPlugin
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IndexActionsSectionTest {
    @Test
    fun `info card shows when no gesture routes to the index agent`() {
        assertTrue(
            nothingRoutesToIndexAgent(
                mapOf(
                    RingGesture.Hold to GestureDestination.WebSearch,
                    RingGesture.ClickHold to GestureDestination.WebhookOnly,
                    RingGesture.Click to GestureDestination.PlayPause,
                )
            )
        )
    }

    @Test
    fun `info card hidden when any recording gesture routes to the index agent`() {
        assertFalse(
            nothingRoutesToIndexAgent(
                mapOf(
                    RingGesture.Hold to GestureDestination.WebSearch,
                    RingGesture.ClickHold to GestureDestination.IndexAgent,
                )
            )
        )
    }

    @Test
    fun `info card hidden when a recording gesture routes to the local agent`() {
        assertFalse(
            nothingRoutesToIndexAgent(
                mapOf(
                    RingGesture.Hold to GestureDestination.LocalAgent,
                    RingGesture.ClickHold to GestureDestination.WebSearch,
                )
            )
        )
    }

    @Test
    fun `capture sentence labels follow the capture type`() {
        assertEquals(Res.string.settings_actions_capture_type_note, captureTypeLabel(DefaultCaptureType.Note))
        assertEquals(Res.string.settings_actions_capture_type_reminder, captureTypeLabel(DefaultCaptureType.Reminder))
        assertEquals(
            Notion.title,
            captureDestinationLabel(DefaultCaptureType.Note, Notion, GoogleTasks)
        )
        assertEquals(
            GoogleTasks.title,
            captureDestinationLabel(DefaultCaptureType.Reminder, Notion, GoogleTasks)
        )
    }

    @Test
    fun `built-in providers are branded Index on the settings screen`() {
        assertEquals("Index Notes", Index.notesTitle)
        assertEquals("Index Reminders", Index.remindersTitle)
        assertEquals(
            "Index Reminders",
            captureDestinationLabel(DefaultCaptureType.Reminder, Index, Index)
        )
    }

    @Test
    fun `other providers use their own title for both kinds`() {
        assertEquals(Notion.title, Notion.notesTitle)
        assertEquals(GoogleTasks.title, GoogleTasks.remindersTitle)
    }

    @Test
    fun `a destination is configurable once connected with a locker page or its own dialog`() {
        val notion = DestinationItem.Installed(Notion, "Notion", connected = true, hasLockerPage = false)
        val plugin = DestinationItem.Installed(Tasker, "Tasker", connected = true, hasLockerPage = true)
        val unconnected = plugin.copy(connected = false)
        val hasDialog = { provider: IntegrationProvider -> noteConfigureDialog(provider) != null }

        assertTrue(notion.isConfigurable(hasDialog))
        assertFalse(notion.isConfigurable())
        assertTrue(plugin.isConfigurable())
        assertFalse(unconnected.isConfigurable(hasDialog))
        assertFalse(DestinationItem.InStore(StorePlugin(FakePlugin(name = "Things").metadata, configurable = true)).isConfigurable())
    }

    @Test
    fun `row subtitles say not connected, then hold to reconfigure`() {
        val connected = DestinationItem.Installed(Notion, "Notion", connected = true, hasLockerPage = false)
        val inStore = DestinationItem.InStore(StorePlugin(FakePlugin(name = "Things").metadata, configurable = false))

        assertNull(connected.rowSubtitle(configurable = false))
        assertEquals(Res.string.settings_destination_hold_to_reconfigure, connected.rowSubtitle(configurable = true))
        assertEquals(Res.string.settings_destination_not_connected, connected.copy(connected = false).rowSubtitle(configurable = false))
        assertEquals(Res.string.settings_destination_not_connected, inStore.rowSubtitle(configurable = false))
    }

    @Test
    fun `connecting a provider opens that provider's own flow`() {
        assertEquals(ActionsDialog.Notion, noteConnectDialog(Notion))
        assertEquals(ActionsDialog.Obsidian, noteConnectDialog(Obsidian))
        assertEquals(ActionsDialog.Tasker, noteConnectDialog(Tasker))
        assertEquals(ActionsDialog.GoogleTasks, reminderConnectDialog(GoogleTasks))
        assertEquals(ActionsDialog.Tasker, reminderConnectDialog(Tasker))
    }

    @Test
    fun `an unconnected calendar offers the phone calendar connect flow`() {
        val disconnected = IndexAction(
            name = CalendarServlet.NAME,
            title = "Calendar",
            enabled = true,
            disabledReason = NOT_CONNECTED_REASON,
        )
        assertEquals(ActionsDialog.PhoneCalendar, actionConnectDialog(disconnected))

        assertNull(actionConnectDialog(disconnected.copy(disabledReason = null)))
        assertNull(actionConnectDialog(disconnected.copy(disabledReason = LOCAL_MODEL_REASON)))
        assertNull(
            actionConnectDialog(disconnected.copy(name = NoteServlet.NAME))
        )
    }

    @Test
    fun `connected providers with their own settings can be configured from the sheet`() {
        assertEquals(ActionsDialog.NotionPage, noteConfigureDialog(Notion))
        assertEquals(ActionsDialog.Obsidian, noteConfigureDialog(Obsidian))
        assertNull(noteConfigureDialog(Index))
        assertNull(noteConfigureDialog(Tasker))
    }

    private fun thingsPlugin(vararg kinds: IntegrationSupport) = FakePlugin(
        actions = kinds.map {
            ActionDeclaration(
                name = when (it) {
                    IntegrationSupport.Note -> PblPluginActionNoteIntegration.ACTION_NAME
                    IntegrationSupport.Reminder, IntegrationSupport.List -> PblPluginActionReminderIntegration.ACTION_NAME
                    IntegrationSupport.Agent -> PblPluginActionAgentIntegration.ACTION_NAME
                }
            )
        },
        name = "Things",
        sources = kinds.map {
            SourceDeclaration(
                category = when (it) {
                    IntegrationSupport.Note -> PblPluginActionNoteIntegration.CATEGORY
                    IntegrationSupport.Reminder, IntegrationSupport.List -> PblPluginActionReminderIntegration.CATEGORY
                    IntegrationSupport.Agent -> PblPluginActionAgentIntegration.CATEGORY
                },
                items = listOf("configured"),
            )
        },
    )

    @Test
    fun `only the built-in feed, iPhone Reminders and plugins are offered`() {
        val plugin = PblPluginIntegrationProvider.forPlugin(thingsPlugin(IntegrationSupport.Note).metadata, FakePlugins())!!
        val all = listOf(Index, Notion, Obsidian, GoogleTasks, IosReminders, Tasker, plugin)

        assertEquals(listOf(Index, IosReminders, plugin), all.visibleDestinations())
    }

    @Test
    fun `store plugins know the kinds their manifest can take`() {
        val both = StorePlugin(thingsPlugin(IntegrationSupport.Note, IntegrationSupport.Reminder).metadata, configurable = false)

        assertEquals("Things", both.title)
        assertEquals(
            setOf(IntegrationSupport.Note, IntegrationSupport.Reminder, IntegrationSupport.List),
            both.supports,
        )
        assertTrue(DestinationItem.InStore(both).isPlugin)
    }

    @Test
    fun `providers with no connect flow fall back to the integration list`() {
        assertNull(noteConnectDialog(Index))
        assertNull(reminderConnectDialog(Index))
        assertNull(reminderConnectDialog(IosReminders))
    }

    private fun plugin(
        actions: PluginGrantState? = PluginGrantState(names = listOf("next", "pause"), enabled = true),
        sources: PluginGrantState? = PluginGrantState(names = listOf("music/now_playing"), enabled = true),
    ) = IndexPlugin(
        pluginUuid = Uuid.parse("00000000-0000-0000-0002-00000000000b"),
        title = "Music",
        actions = actions,
        sources = sources,
    )

    @Test
    fun `plugin parent checkbox is indeterminate while grants disagree`() {
        assertEquals(ToggleableState.On, pluginToggleState(plugin()))
        assertEquals(
            ToggleableState.Indeterminate,
            pluginToggleState(plugin(sources = PluginGrantState(listOf("music/now_playing"), enabled = false))),
        )
        assertEquals(
            ToggleableState.Off,
            pluginToggleState(
                plugin(
                    actions = PluginGrantState(listOf("next"), enabled = false),
                    sources = PluginGrantState(listOf("music/now_playing"), enabled = false),
                )
            ),
        )
        assertEquals(ToggleableState.On, pluginToggleState(plugin(sources = null)))
    }

    @Test
    fun `tapping the parent turns everything on unless it already is`() {
        assertEquals(
            listOf(PluginGrant.Actions to false, PluginGrant.Sources to false),
            pluginToggleChanges(plugin()),
        )
        val mixed = plugin(sources = PluginGrantState(listOf("music/now_playing"), enabled = false))
        assertEquals(listOf(PluginGrant.Sources to true), pluginToggleChanges(mixed))
    }

    @Test
    fun `tapping the parent leaves grants the user cannot change alone`() {
        val gated = plugin(
            actions = PluginGrantState(listOf("next"), enabled = false, disabledReason = LOCAL_MODEL_REASON),
            sources = PluginGrantState(listOf("music/now_playing"), enabled = false),
        )
        assertEquals(listOf(PluginGrant.Sources to true), pluginToggleChanges(gated))
    }

    @Test
    fun `plugin summary counts each grant`() {
        val actions = Res.plurals.settings_actions_plugin_actions_count
        val sources = Res.plurals.settings_actions_plugin_sources_count
        assertEquals(listOf(actions to 2, sources to 1), pluginGrantCounts(plugin()))
        assertEquals(listOf(actions to 1), pluginGrantCounts(plugin(actions = PluginGrantState(listOf("next"), true), sources = null)))
        assertEquals(listOf(sources to 2), pluginGrantCounts(plugin(actions = null, sources = PluginGrantState(listOf("a/b", "a/c"), true))))
    }

    @Test
    fun `grant labels`() {
        assertEquals(Res.string.settings_actions_grant_actions, grantLabel(PluginGrant.Actions))
        assertEquals(Res.string.settings_actions_grant_sources, grantLabel(PluginGrant.Sources))
    }
}
