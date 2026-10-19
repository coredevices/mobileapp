package io.rebble.libpebblecommon.calls

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NotificationCallTrackerTest {
    private val ringing = NotificationCall("Alice", "Incoming video call", ringing = true)
    private val active = NotificationCall("Alice", "Calling...", ringing = false)

    private val currentCall = MutableStateFlow<Call?>(null)
    private val notifications = MutableSharedFlow<NotificationCall?>()
    private var answered = 0
    private var ended = 0

    private fun TestScope.startTracker() {
        val tracker = NotificationCallTracker(currentCall, { answered++ }, { ended++ })
        backgroundScope.launch { tracker.track(notifications) }
        runCurrent()
    }

    private suspend fun TestScope.post(notification: NotificationCall?, after: Duration = 0.seconds) {
        notifications.emit(notification)
        advanceTimeBy(after)
        runCurrent()
    }

    @Test
    fun ringingNotificationRingsTheWatch() = runTest {
        startTracker()
        post(ringing, after = 1.1.seconds)

        val call = assertIs<Call.RingingCall>(currentCall.value)
        assertEquals("Alice", call.contactName)
        call.answerCall()
        assertEquals(1, answered)
    }

    @Test
    fun callClaimedBySystemIsLeftAlone() = runTest {
        startTracker()
        post(ringing, after = 500.milliseconds)
        val systemCall = Call.RingingCall("Bob", "123", 1u, {}, {})
        currentCall.value = systemCall
        post(null, after = 2.seconds)

        assertEquals(systemCall, currentCall.value)
    }

    @Test
    fun answeringWithRepostBecomesActiveThenEnds() = runTest {
        startTracker()
        post(ringing, after = 8.seconds)
        val cookie = assertIs<Call.RingingCall>(currentCall.value).cookie

        post(null, after = 1.15.seconds)
        post(ringing, after = 1.4.seconds)
        assertIs<Call.RingingCall>(currentCall.value)
        assertEquals(cookie, currentCall.value?.cookie)

        post(active, after = 20.seconds)
        assertIs<Call.ActiveCall>(currentCall.value)
        assertEquals(cookie, currentCall.value?.cookie)

        post(null, after = 3.1.seconds)
        assertNull(currentCall.value)
    }

    @Test
    fun repostWhileRingingDoesNotRingAgain() = runTest {
        startTracker()
        post(ringing, after = 2.seconds)
        val cookie = currentCall.value?.cookie

        post(null, after = 1.45.seconds)
        post(ringing, after = 2.seconds)

        assertIs<Call.RingingCall>(currentCall.value)
        assertEquals(cookie, currentCall.value?.cookie)
    }

    @Test
    fun callMissedBeforeClaimDelayNeverRings() = runTest {
        startTracker()
        post(ringing, after = 300.milliseconds)
        post(null, after = 2.seconds)

        assertNull(currentCall.value)
    }

    @Test
    fun hangupFromWatchUsesEndAction() = runTest {
        startTracker()
        post(ringing, after = 1.1.seconds)
        assertIs<Call.RingingCall>(currentCall.value).endCall()

        assertEquals(1, ended)
    }
}
