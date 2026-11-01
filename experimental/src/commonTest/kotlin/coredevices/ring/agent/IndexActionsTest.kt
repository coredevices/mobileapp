package coredevices.ring.agent

import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.notifications.NotificationReplyServlet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexActionsTest {

    private fun actions(
        isAndroid: Boolean,
        localAgentOnly: Boolean = false,
        calendarConnected: Boolean = true,
        watchPaired: Boolean = true,
    ) = builtinServletDefinitions(isAndroid).associate {
        it.name to it.toIndexAction(
            enabled = true,
            localAgentOnly = localAgentOnly,
            calendarConnected = calendarConnected,
            watchPaired = watchPaired,
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
        assertNull(android.getValue(NotificationReplyServlet.NAME).unavailableReason)
        assertEquals(ANDROID_ONLY_REASON, ios.getValue(NotificationReplyServlet.NAME).unavailableReason)
        assertTrue(ios.getValue(NoteServlet.NAME).available)
    }

    @Test
    fun anActionUnavailableOnThisPlatformIsNeverEnabled() {
        val clock = actions(isAndroid = false)
            .getValue(ClockServlet.name)

        assertEquals(ANDROID_ONLY_REASON, clock.disabledReason)
        assertFalse(clock.enabled)
    }

    @Test
    fun localAgentOnlyGatesActionsTheOnDeviceModelCannotRun() {
        val local = actions(isAndroid = true, localAgentOnly = true)

        assertEquals(LOCAL_MODEL_REASON, local.getValue(CalendarServlet.NAME).disabledReason)
        assertEquals(LOCAL_MODEL_REASON, local.getValue(MessagingServlet.name).disabledReason)
        assertEquals(LOCAL_MODEL_REASON, local.getValue(NotificationReplyServlet.NAME).disabledReason)
        assertNull(local.getValue(NoteServlet.NAME).disabledReason)
        assertNull(local.getValue(ClockServlet.name).disabledReason)
    }

    @Test
    fun hybridAgentLeavesEveryActionUsable() {
        val hybrid = actions(isAndroid = true)

        assertTrue(hybrid.values.all { it.disabledReason == null && it.enabled })
    }

    @Test
    fun calendarIsNotConnectedUntilItIsLinkedAndPermitted() {
        val disconnected = actions(isAndroid = true, calendarConnected = false)
        assertEquals(
            NOT_CONNECTED_REASON,
            disconnected.getValue(CalendarServlet.NAME).disabledReason,
        )

        val connected = actions(isAndroid = true)
        assertNull(connected.getValue(CalendarServlet.NAME).disabledReason)
    }

    @Test
    fun replyToNotificationsNeedsAPairedPebble() {
        val unpaired = actions(isAndroid = true, watchPaired = false)
        assertEquals(NO_PEBBLE_REASON, unpaired.getValue(NotificationReplyServlet.NAME).disabledReason)
        assertNull(unpaired.getValue(MessagingServlet.name).disabledReason)

        val paired = actions(isAndroid = true)
        assertNull(paired.getValue(NotificationReplyServlet.NAME).disabledReason)
    }
}
