package coredevices.ring.ui.screens.settings

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
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.PblPluginActionNoteIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginActionReminderIntegration
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
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
    fun `info card hidden when a recording gesture routes to the local or remote agent`() {
        listOf(GestureDestination.LocalAgent, GestureDestination.RemoteAgent).forEach { agent ->
            assertFalse(
                nothingRoutesToIndexAgent(
                    mapOf(
                        RingGesture.Hold to agent,
                        RingGesture.ClickHold to GestureDestination.WebSearch,
                    )
                ),
                "$agent",
            )
        }
    }

    @Test
    fun `capture sentence labels follow the capture type`() {
        assertEquals("note", captureTypeLabel(DefaultCaptureType.Note))
        assertEquals("reminder", captureTypeLabel(DefaultCaptureType.Reminder))
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
    fun `contacts badge pluralises`() {
        assertEquals("1 contact", contactsBadge(1))
        assertEquals("2 contacts", contactsBadge(2))
        assertEquals("0 contacts", contactsBadge(0))
    }

    @Test
    fun `note destinations list every provider and mark unauthorized ones as unconnected`() {
        val options = noteDestOptions(
            providers = listOf(Index, Notion, Obsidian, Tasker),
            available = listOf(Index),
        )
        assertEquals(listOf(Index, Notion, Obsidian, Tasker), options.map { it.provider })
        assertEquals(listOf(Index), options.filter { it.connected }.map { it.provider })
        assertEquals("Index Notes", options.first().title)
    }

    @Test
    fun `reminder destinations use the reminders label`() {
        val options = reminderDestOptions(
            providers = listOf(Index, GoogleTasks),
            available = listOf(Index, GoogleTasks),
        )
        assertEquals(listOf("Index Reminders", "Google Tasks"), options.map { it.title })
        assertTrue(options.all { it.connected })
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

    @Test
    fun `note destinations are only configurable once connected`() {
        val options = noteDestOptions(
            providers = listOf(Index, Notion, Obsidian),
            available = listOf(Index, Obsidian),
        )
        assertEquals(listOf(Obsidian), options.filter { it.configurable }.map { it.provider })
        assertFalse(
            noteDestOptions(providers = listOf(Index, Obsidian), available = emptyList()).any { it.configurable }
        )
    }

    @Test
    fun `plugin destinations are configurable once connected and they have a config page`() {
        val plugin = PblPluginIntegrationProvider.forPlugin(
            FakePlugin(
                actions = listOf(
                    ActionDeclaration(name = PblPluginActionNoteIntegration.ACTION_NAME),
                    ActionDeclaration(name = PblPluginActionReminderIntegration.ACTION_NAME),
                ),
                name = "Things",
                sources = listOf(
                    SourceDeclaration(category = PblPluginActionNoteIntegration.CATEGORY, items = listOf("configured")),
                    SourceDeclaration(category = PblPluginActionReminderIntegration.CATEGORY, items = listOf("configured")),
                ),
            )
        )!!
        val withPage = setOf(plugin.pluginUuid)

        assertTrue(noteDestOptions(listOf(plugin), listOf(plugin), withPage).single().configurable)
        assertTrue(reminderDestOptions(listOf(plugin), listOf(plugin), withPage).single().configurable)
        assertFalse(noteDestOptions(listOf(plugin), listOf(plugin)).single().configurable)
        assertFalse(noteDestOptions(listOf(plugin), emptyList(), withPage).single().configurable)
        assertFalse(reminderDestOptions(listOf(GoogleTasks), listOf(GoogleTasks), withPage).single().configurable)
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
        assertEquals("2 actions · 1 data source", pluginSummary(plugin()))
        assertEquals("1 action", pluginSummary(plugin(actions = PluginGrantState(listOf("next"), true), sources = null)))
        assertEquals(
            "2 data sources",
            pluginSummary(plugin(actions = null, sources = PluginGrantState(listOf("a/b", "a/c"), true))),
        )
        assertEquals("Use actions", grantLabel(PluginGrant.Actions))
        assertEquals("Use data sources", grantLabel(PluginGrant.Sources))
    }
}
