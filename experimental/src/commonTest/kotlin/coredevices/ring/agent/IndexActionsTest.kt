package coredevices.ring.agent

import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexActionsTest {

    private fun actions(
        isAndroid: Boolean,
        llmMode: LlmMode,
        calendarConnected: Boolean = true,
    ) = builtinServletDefinitions(isAndroid).associate {
        it.name to it.toIndexAction(
            enabled = true,
            llmMode = llmMode,
            calendarConnected = calendarConnected,
        )
    }

    @Test
    fun androidOnlyActionsAreListedOnIosWithAReason() {
        val android = builtinServletDefinitions(isAndroid = true).associateBy { it.name }
        val ios = builtinServletDefinitions(isAndroid = false).associateBy { it.name }

        assertEquals(android.keys, ios.keys)
        assertNull(android.getValue(ClockServlet.name).unavailableReason)
        assertNull(android.getValue(MessagingServlet.name).unavailableReason)
        assertEquals(ANDROID_ONLY_REASON, ios.getValue(ClockServlet.name).unavailableReason)
        assertEquals(ANDROID_ONLY_REASON, ios.getValue(MessagingServlet.name).unavailableReason)
        assertTrue(ios.getValue(NoteServlet.NAME).available)
    }

    @Test
    fun anActionUnavailableOnThisPlatformIsNeverEnabled() {
        val clock = actions(isAndroid = false, llmMode = LlmMode.RemoteOnly)
            .getValue(ClockServlet.name)

        assertEquals(ANDROID_ONLY_REASON, clock.disabledReason)
        assertFalse(clock.enabled)
    }

    @Test
    fun localOnlyModeGatesActionsTheOnDeviceModelCannotRun() {
        val local = actions(isAndroid = true, llmMode = LlmMode.LocalOnly)

        assertEquals(LOCAL_MODEL_REASON, local.getValue(CalendarServlet.NAME).disabledReason)
        assertEquals(LOCAL_MODEL_REASON, local.getValue(MessagingServlet.name).disabledReason)
        assertNull(local.getValue(NoteServlet.NAME).disabledReason)
        assertNull(local.getValue(ClockServlet.name).disabledReason)
    }

    @Test
    fun remoteFirstModeLeavesEveryActionUsable() {
        val remoteFirst = actions(isAndroid = true, llmMode = LlmMode.RemoteFirst)

        assertTrue(remoteFirst.values.all { it.disabledReason == null && it.enabled })
    }

    @Test
    fun calendarIsNotConnectedUntilItIsLinkedAndPermitted() {
        val disconnected = actions(isAndroid = true, llmMode = LlmMode.RemoteOnly, calendarConnected = false)
        assertEquals(
            NOT_CONNECTED_REASON,
            disconnected.getValue(CalendarServlet.NAME).disabledReason,
        )

        val connected = actions(isAndroid = true, llmMode = LlmMode.RemoteOnly)
        assertNull(connected.getValue(CalendarServlet.NAME).disabledReason)
    }
}
