package coredevices.ring.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RingScanWatchdogTest {

    private val advertisements = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var recentConnection = false
    private var restarts = 0

    private fun TestScope.watchdog() = RingScanWatchdog(
        advertisements = advertisements,
        hadRecentConnection = { recentConnection },
        restartScan = { restarts++ },
        timeSource = testScheduler.timeSource,
    ).also {
        it.start(backgroundScope)
        runCurrent()
    }

    @Test
    fun recentAdvertisementNeedsNoRestart() = runTest {
        val watchdog = watchdog()
        advertisements.emit(Unit)
        runCurrent()
        advanceTimeBy(2.seconds)
        watchdog.onRingPresent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(0, restarts)
    }

    @Test
    fun advertisementDuringWaitNeedsNoRestart() = runTest {
        val watchdog = watchdog()
        watchdog.onRingPresent()
        runCurrent()
        advanceTimeBy(500.milliseconds)
        advertisements.emit(Unit)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(0, restarts)
    }

    @Test
    fun silentScanIsRestarted() = runTest {
        val watchdog = watchdog()
        advertisements.emit(Unit)
        runCurrent()
        advanceTimeBy(5.seconds)
        watchdog.onRingPresent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, restarts)
    }

    @Test
    fun neverAdvertisedScanIsRestarted() = runTest {
        val watchdog = watchdog()
        watchdog.onRingPresent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, restarts)
    }

    @Test
    fun silentScanLeftAloneAfterRecentConnection() = runTest {
        recentConnection = true
        val watchdog = watchdog()
        watchdog.onRingPresent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(0, restarts)
    }

    @Test
    fun overlappingPresenceEventsCheckOnce() = runTest {
        val watchdog = watchdog()
        assertNotNull(watchdog.onRingPresent())
        runCurrent()
        assertNull(watchdog.onRingPresent())
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, restarts)
    }

    @Test
    fun checkCompletesWhenRestartedScanAdvertises() = runTest {
        val watchdog = watchdog()
        val check = assertNotNull(watchdog.onRingPresent())
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, restarts)
        assertFalse(check.isCompleted)
        advertisements.emit(Unit)
        runCurrent()
        assertTrue(check.isCompleted)
    }

    @Test
    fun checkGivesUpWhenRestartedScanStaysSilent() = runTest {
        val watchdog = watchdog()
        val check = assertNotNull(watchdog.onRingPresent())
        advanceTimeBy(5.seconds)
        runCurrent()
        assertFalse(check.isCompleted)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(check.isCompleted)
        assertEquals(1, restarts)
    }

    @Test
    fun notStartedIsNoOp() = runTest {
        RingScanWatchdog(advertisements, { false }, { restarts++ }).onRingPresent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(0, restarts)
    }
}
