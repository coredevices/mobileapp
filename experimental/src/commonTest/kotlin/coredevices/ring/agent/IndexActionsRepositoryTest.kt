package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.mcp.client.McpIntegration
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.pebble_plugin.FakePlugin
import coredevices.ring.agent.pebble_plugin.PluginGrant
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.Plugin
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.WatchStatePlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IndexActionsRepositoryTest {

    private class FakeServletRepository(private val isAndroid: Boolean) : ServletRepository {
        override fun getAllServlets(): List<McpServerDefinition> =
            builtinServletDefinitions(isAndroid)

        override fun resolveName(name: String): McpIntegration? = null
    }

    private class Fixture(
        isAndroid: Boolean,
        localAgentOnly: Boolean = false,
        calendarConnected: Boolean = true,
        beeperUnavailable: String? = null,
        plugins: List<Plugin> = emptyList(),
        watchPaired: Boolean = true,
    ) {
        val watch = MutableStateFlow(watchPaired)
        val calendar = MutableStateFlow(calendarConnected)
        val beeper = MutableStateFlow(beeperUnavailable)
        val installed = plugins.toMutableList()
        val disabledPlugins = PluginGrant.entries.associateWith { MutableStateFlow<Set<Uuid>>(emptySet()) }
        val disabledBuiltins = MutableStateFlow<Set<String>>(emptySet())
        val repo = IndexActionsRepository(
            servletRepository = FakeServletRepository(isAndroid),
            disabledBuiltins = { disabledBuiltins },
            setBuiltinEnabled = { name, enabled ->
                disabledBuiltins.value =
                    if (enabled) disabledBuiltins.value - name else disabledBuiltins.value + name
            },
            localAgentOnly = MutableStateFlow(localAgentOnly),
            calendarConnected = calendar,
            beeperUnavailable = beeper,
            installedPlugins = { installed.map { it.metadata } },
            disabledPlugins = { disabledPlugins.getValue(it) },
            persistPluginGrantEnabled = { uuid, grant, enabled ->
                val flow = disabledPlugins.getValue(grant)
                flow.value = if (enabled) flow.value - uuid else flow.value + uuid
            },
            watchPaired = watch,
        )
    }

    private val watchState = FakePlugin(
        actions = listOf(ActionDeclaration(name = "launch_app")),
        name = "Watch State",
        pluginUuid = WatchStatePlugin.BUILT_IN_WATCH_STATE_UUID,
    )

    private val thingsUuid = Uuid.parse("00000000-0000-0000-0002-000000000001")
    private val things = FakePlugin(
        actions = listOf(ActionDeclaration(name = "create_todo"), ActionDeclaration(name = "complete_todo")),
        name = "Things",
        pluginUuid = thingsUuid,
    )
    private val weatherUuid = Uuid.parse("00000000-0000-0000-0002-000000000002")
    private val weather = FakePlugin(
        name = "Weather",
        pluginUuid = weatherUuid,
        sources = listOf(SourceDeclaration(category = "weather", items = listOf("current"))),
    )
    private val both = FakePlugin(
        actions = listOf(ActionDeclaration(name = "toggle")),
        name = "Home",
        pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000003"),
        sources = listOf(SourceDeclaration(category = "home", items = listOf("light", "lock"))),
    )
    private val empty = FakePlugin(name = "Empty", pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000004"))

    @Test
    fun androidOnlyActionsAreListedButUnavailableOnIos() {
        val android = builtinServletDefinitions(isAndroid = true).associateBy { it.name }
        val ios = builtinServletDefinitions(isAndroid = false).associateBy { it.name }

        assertEquals(android.keys, ios.keys)
        assertTrue(ClockServlet.name in ios)
        assertTrue(MessagingServlet.name in ios)

        assertNull(android.getValue(ClockServlet.name).unavailableReason)
        assertNull(android.getValue(MessagingServlet.name).unavailableReason)
        assertEquals(ANDROID_ONLY_REASON, ios.getValue(ClockServlet.name).unavailableReason)
        assertEquals(ANDROID_ONLY_REASON, ios.getValue(MessagingServlet.name).unavailableReason)
        assertTrue(ios.getValue(NoteServlet.NAME).available)
    }

    @Test
    fun androidOnlyActionsSurfaceTheirReasonOnIos() = runTest {
        val actions = Fixture(isAndroid = false).repo.actions.first()
        val clock = actions.single { it.name == ClockServlet.name }
        assertEquals(ANDROID_ONLY_REASON, clock.disabledReason)
        assertEquals(false, clock.enabled)
    }

    @Test
    fun togglingAnActionRoundTripsThroughTheDisabledSet() = runTest {
        val fixture = Fixture(isAndroid = true)

        assertTrue(fixture.repo.actions.first().single { it.name == NoteServlet.NAME }.enabled)

        fixture.repo.setActionEnabled(NoteServlet.NAME, false)
        val disabled = fixture.repo.actions.first()
        assertEquals(false, disabled.single { it.name == NoteServlet.NAME }.enabled)
        assertTrue(disabled.single { it.name == CalendarServlet.NAME }.enabled)

        fixture.repo.setActionEnabled(NoteServlet.NAME, true)
        assertTrue(fixture.repo.actions.first().single { it.name == NoteServlet.NAME }.enabled)
    }

    @Test
    fun localOnlyModeGatesActionsTheOnDeviceModelCannotRun() = runTest {
        val local = Fixture(isAndroid = true, localAgentOnly = true).repo
        val localActions = local.actions.first()
        assertEquals(
            LOCAL_MODEL_REASON,
            localActions.single { it.name == CalendarServlet.NAME }.disabledReason
        )
        assertEquals(
            LOCAL_MODEL_REASON,
            localActions.single { it.name == MessagingServlet.name }.disabledReason
        )
        assertNull(localActions.single { it.name == NoteServlet.NAME }.disabledReason)
        assertNull(localActions.single { it.name == ClockServlet.name }.disabledReason)
        assertNotNull(local.httpMcpDisabledReason.first())

        val remote = Fixture(isAndroid = true, localAgentOnly = false).repo
        val remoteActions = remote.actions.first()
        assertNull(remoteActions.single { it.name == CalendarServlet.NAME }.disabledReason)
        assertNull(remoteActions.single { it.name == MessagingServlet.name }.disabledReason)
        assertNull(remote.httpMcpDisabledReason.first())
    }

    @Test
    fun calendarReportsNotConnectedUntilItIsLinkedAndPermitted() = runTest {
        val fixture = Fixture(isAndroid = true, calendarConnected = false)

        val disconnected = fixture.repo.actions.first().single { it.name == CalendarServlet.NAME }
        assertEquals(NOT_CONNECTED_REASON, disconnected.disabledReason)

        fixture.calendar.value = true
        assertNull(fixture.repo.actions.first().single { it.name == CalendarServlet.NAME }.disabledReason)
    }

    @Test
    fun notConnectedOnlyAppliesToCalendar() = runTest {
        val actions = Fixture(isAndroid = true, calendarConnected = false)
            .repo.actions.first()

        assertNull(actions.single { it.name == NoteServlet.NAME }.disabledReason)
        assertNull(actions.single { it.name == ClockServlet.name }.disabledReason)
    }

    @Test
    fun localModelReasonWinsOverNotConnectedForCalendar() = runTest {
        val actions = Fixture(isAndroid = true, localAgentOnly = true, calendarConnected = false)
            .repo.actions.first()

        assertEquals(
            LOCAL_MODEL_REASON,
            actions.single { it.name == CalendarServlet.NAME }.disabledReason,
        )
    }

    @Test
    fun beeperReportsWhyItCannotBeUsed() = runTest {
        val notInstalled = Fixture(
            isAndroid = true,
            beeperUnavailable = "Beeper isn't installed on this phone",
        ).repo.actions.first()
        assertEquals(
            "Beeper isn't installed on this phone",
            notInstalled.single { it.name == MessagingServlet.name }.disabledReason,
        )

        val ready = Fixture(isAndroid = true).repo.actions.first()
        assertNull(ready.single { it.name == MessagingServlet.name }.disabledReason)
    }

    @Test
    fun beeperReasonOnlyAppliesToMessaging() = runTest {
        val actions = Fixture(
            isAndroid = true,
            beeperUnavailable = "Allow access to Beeper in your phone's settings",
        ).repo.actions.first()

        assertNull(actions.single { it.name == NoteServlet.NAME }.disabledReason)
        assertNull(actions.single { it.name == CalendarServlet.NAME }.disabledReason)
    }

    @Test
    fun beingOnIosOutranksTheBeeperReason() = runTest {
        val actions = Fixture(
            isAndroid = false,
            beeperUnavailable = "Beeper isn't installed on this phone",
        ).repo.actions.first()

        val messaging = actions.single { it.name == MessagingServlet.name }
        assertEquals(ANDROID_ONLY_REASON, messaging.disabledReason)
        assertEquals(false, messaging.enabled)
    }

    @Test
    fun pluginsListEachGrantTheyOfferAndAreOnByDefault() = runTest {
        val fixture = Fixture(
            isAndroid = true,
            plugins = listOf(things, weather, both, empty),
        )

        val plugins = fixture.repo.plugins.first()
        assertEquals(listOf("Things", "Weather", "Home"), plugins.map { it.title })

        val actionsOnly = plugins.single { it.pluginUuid == thingsUuid }
        assertEquals(listOf("create_todo", "complete_todo"), actionsOnly.actions?.names)
        assertNull(actionsOnly.sources)
        assertTrue(actionsOnly.actions!!.enabled)
        assertNull(actionsOnly.actions!!.disabledReason)

        val sourcesOnly = plugins.single { it.pluginUuid == weatherUuid }
        assertNull(sourcesOnly.actions)
        assertEquals(listOf("weather/current"), sourcesOnly.sources?.names)
        assertTrue(sourcesOnly.sources!!.enabled)

        val home = plugins.single { it.title == "Home" }
        assertEquals(listOf("toggle"), home.actions?.names)
        assertEquals(listOf("home/light", "home/lock"), home.sources?.names)
        assertEquals(setOf(PluginGrant.Actions, PluginGrant.Sources), home.grants.keys)
    }

    @Test
    fun localOnlyModeGatesPluginActionsButNotSources() = runTest {
        val plugin = Fixture(isAndroid = true, localAgentOnly = true, plugins = listOf(both))
            .repo.plugins.first().single()
        assertEquals(LOCAL_MODEL_REASON, plugin.actions?.disabledReason)
        assertNull(plugin.sources?.disabledReason)
    }

    @Test
    fun togglingAGrantRoundTripsThroughItsOwnDisabledSet() = runTest {
        val fixture = Fixture(isAndroid = true, plugins = listOf(both))
        val uuid = both.pluginUuid

        fixture.repo.setPluginGrantEnabled(uuid, PluginGrant.Sources, false)
        val plugin = fixture.repo.plugins.first().single()
        assertTrue(plugin.actions!!.enabled)
        assertEquals(false, plugin.sources!!.enabled)
        assertEquals(emptySet(), fixture.disabledPlugins.getValue(PluginGrant.Actions).value)
        assertEquals(setOf(uuid), fixture.disabledPlugins.getValue(PluginGrant.Sources).value)

        fixture.repo.setPluginGrantEnabled(uuid, PluginGrant.Actions, false)
        fixture.repo.setPluginGrantEnabled(uuid, PluginGrant.Sources, true)
        val flipped = fixture.repo.plugins.first().single()
        assertEquals(false, flipped.actions!!.enabled)
        assertTrue(flipped.sources!!.enabled)
        assertTrue(fixture.repo.actions.first().single { it.name == NoteServlet.NAME }.enabled)
    }

    @Test
    fun refreshingPicksUpNewlyInstalledPlugins() = runTest {
        val fixture = Fixture(isAndroid = true)
        assertTrue(fixture.repo.plugins.first().isEmpty())

        fixture.installed += things
        fixture.repo.refreshPlugins()
        assertEquals(listOf("Things"), fixture.repo.plugins.first().map { it.title })
    }

    @Test
    fun watchPluginsAreHiddenUntilAWatchIsPaired() = runTest {
        val fixture = Fixture(isAndroid = true, plugins = listOf(things, watchState), watchPaired = false)
        assertEquals(listOf("Things"), fixture.repo.plugins.first().map { it.title })

        fixture.watch.value = true
        assertEquals(listOf("Things", "Watch State"), fixture.repo.plugins.first().map { it.title })
    }

    @Test
    fun grantedPluginsFollowTheGrantOfferedTheDisabledSetAndTheWatch() = runTest {
        val fixture = Fixture(
            isAndroid = true,
            plugins = listOf(things, weather, both, empty, watchState),
            watchPaired = false,
        )
        val repo = fixture.repo

        assertEquals(listOf("Things", "Home"), repo.grantedPlugins(PluginGrant.Actions).first().map { it.name })
        assertEquals(listOf("Weather", "Home"), repo.grantedPlugins(PluginGrant.Sources).first().map { it.name })

        repo.setPluginGrantEnabled(both.pluginUuid, PluginGrant.Actions, false)
        assertEquals(listOf("Things"), repo.grantedPlugins(PluginGrant.Actions).first().map { it.name })
        assertEquals(listOf("Weather", "Home"), repo.grantedPlugins(PluginGrant.Sources).first().map { it.name })

        fixture.watch.value = true
        assertEquals(
            listOf("Things", "Watch State"),
            repo.grantedPlugins(PluginGrant.Actions).first().map { it.name },
        )
    }
}
