package coredevices.util

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecondaryProfileWarningTest {

    @Test
    fun notPendingOnMainProfile() = runBlocking {
        val warning = SecondaryProfileWarning(MapSettings(), isSecondaryProfile = false)
        assertFalse(warning.pending)
        warning.awaitDecision()
    }

    @Test
    fun notPendingOnceSeen() = runBlocking {
        val settings = MapSettings("hasSeenSecondaryProfileWarning" to true)
        val warning = SecondaryProfileWarning(settings, isSecondaryProfile = true)
        assertFalse(warning.pending)
        warning.awaitDecision()
    }

    @Test
    fun awaitDecisionBlocksUntilMarkedSeen() = runTest {
        val settings = MapSettings()
        val warning = SecondaryProfileWarning(settings, isSecondaryProfile = true)
        assertTrue(warning.pending)
        var released = false
        val waiter = launch { warning.awaitDecision(); released = true }
        testScheduler.runCurrent()
        assertFalse(released)
        warning.markSeen()
        waiter.join()
        assertTrue(released)
        assertFalse(warning.pending)
        assertTrue(settings.getBoolean("hasSeenSecondaryProfileWarning", false))
    }
}
