package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.mcp.client.McpIntegration
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.database.room.repository.McpServerEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexActionsRepositoryTest {

    private class FakeServletRepository(private val isAndroid: Boolean) : ServletRepository {
        override fun getAllServlets(): List<McpServerDefinition> =
            builtinServletDefinitions(isAndroid)

        override fun resolveName(name: String): McpIntegration? = null
    }

    private class Fixture(
        isAndroid: Boolean,
        llmMode: LlmMode,
        calendarConnected: Boolean = true,
        beeperUnavailable: String? = null,
    ) {
        val calendar = MutableStateFlow(calendarConnected)
        val beeper = MutableStateFlow(beeperUnavailable)
        val entries = MutableStateFlow<List<McpServerEntry>>(
            builtinServletDefinitions(isAndroid)
                .filter { it.available }
                .map { McpServerEntry.BuiltinMcpEntry(it.name) }
        )
        val repo = IndexActionsRepository(
            servletRepository = FakeServletRepository(isAndroid),
            defaultGroupEntries = { entries },
            setEnabledInDefaultGroup = { name, enabled ->
                val entry = McpServerEntry.BuiltinMcpEntry(name)
                entries.value =
                    if (enabled) entries.value + entry else entries.value - entry
            },
            llmMode = MutableStateFlow(llmMode),
            calendarConnected = calendar,
            beeperUnavailable = beeper,
        )
    }

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
        val actions = Fixture(isAndroid = false, llmMode = LlmMode.RemoteOnly).repo.actions.first()
        val clock = actions.single { it.name == ClockServlet.name }
        assertEquals(ANDROID_ONLY_REASON, clock.disabledReason)
        assertEquals(false, clock.enabled)
    }

    @Test
    fun togglingAnActionRoundTripsThroughTheDefaultGroup() = runTest {
        val fixture = Fixture(isAndroid = true, llmMode = LlmMode.RemoteOnly)

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
        val local = Fixture(isAndroid = true, llmMode = LlmMode.LocalOnly).repo
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

        val remote = Fixture(isAndroid = true, llmMode = LlmMode.RemoteFirst).repo
        val remoteActions = remote.actions.first()
        assertNull(remoteActions.single { it.name == CalendarServlet.NAME }.disabledReason)
        assertNull(remoteActions.single { it.name == MessagingServlet.name }.disabledReason)
        assertNull(remote.httpMcpDisabledReason.first())
    }

    @Test
    fun calendarReportsNotConnectedUntilItIsLinkedAndPermitted() = runTest {
        val fixture = Fixture(isAndroid = true, llmMode = LlmMode.RemoteOnly, calendarConnected = false)

        val disconnected = fixture.repo.actions.first().single { it.name == CalendarServlet.NAME }
        assertEquals(NOT_CONNECTED_REASON, disconnected.disabledReason)

        fixture.calendar.value = true
        assertNull(fixture.repo.actions.first().single { it.name == CalendarServlet.NAME }.disabledReason)
    }

    @Test
    fun notConnectedOnlyAppliesToCalendar() = runTest {
        val actions = Fixture(isAndroid = true, llmMode = LlmMode.RemoteOnly, calendarConnected = false)
            .repo.actions.first()

        assertNull(actions.single { it.name == NoteServlet.NAME }.disabledReason)
        assertNull(actions.single { it.name == ClockServlet.name }.disabledReason)
    }

    @Test
    fun localModelReasonWinsOverNotConnectedForCalendar() = runTest {
        val actions = Fixture(isAndroid = true, llmMode = LlmMode.LocalOnly, calendarConnected = false)
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
            llmMode = LlmMode.RemoteOnly,
            beeperUnavailable = "Beeper isn't installed on this phone",
        ).repo.actions.first()
        assertEquals(
            "Beeper isn't installed on this phone",
            notInstalled.single { it.name == MessagingServlet.name }.disabledReason,
        )

        val ready = Fixture(isAndroid = true, llmMode = LlmMode.RemoteOnly).repo.actions.first()
        assertNull(ready.single { it.name == MessagingServlet.name }.disabledReason)
    }

    @Test
    fun beeperReasonOnlyAppliesToMessaging() = runTest {
        val actions = Fixture(
            isAndroid = true,
            llmMode = LlmMode.RemoteOnly,
            beeperUnavailable = "Allow access to Beeper in your phone's settings",
        ).repo.actions.first()

        assertNull(actions.single { it.name == NoteServlet.NAME }.disabledReason)
        assertNull(actions.single { it.name == CalendarServlet.NAME }.disabledReason)
    }

    @Test
    fun beingOnIosOutranksTheBeeperReason() = runTest {
        val actions = Fixture(
            isAndroid = false,
            llmMode = LlmMode.RemoteOnly,
            beeperUnavailable = "Beeper isn't installed on this phone",
        ).repo.actions.first()

        val messaging = actions.single { it.name == MessagingServlet.name }
        assertEquals(ANDROID_ONLY_REASON, messaging.disabledReason)
        assertEquals(false, messaging.enabled)
    }
}
