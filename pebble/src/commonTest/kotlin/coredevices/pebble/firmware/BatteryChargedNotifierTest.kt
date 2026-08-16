package coredevices.pebble.firmware

import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.PebbleSocketIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class BatteryChargedNotifierTest {
    private val config = MutableStateFlow(CoreConfig())
    private val notified = mutableListOf<String>()
    private val notifier =
        RealBatteryChargedNotifier(CoreConfigFlow(config)) { notified.add(it) }

    private val watch = PebbleSocketIdentifier("watch-1")
    private val otherWatch = PebbleSocketIdentifier("watch-2")

    private fun levels(vararg levels: Int?, identifier: PebbleSocketIdentifier = watch) {
        levels.forEach { notifier.onBatteryLevel(identifier, identifier.asString, it) }
    }

    @Test
    fun notifiesOnReachingFull() {
        levels(98, 99, 100)
        assertEquals(listOf("watch-1"), notified)
    }

    @Test
    fun doesNotNotifyWhenAlreadyFullOnFirstReading() {
        levels(100, 100)
        assertEquals(emptyList(), notified)
    }

    @Test
    fun doesNotNotifyTwiceWhileHoveringAtFull() {
        levels(99, 100, 99, 100, 98, 100)
        assertEquals(listOf("watch-1"), notified)
    }

    @Test
    fun notifiesAgainAfterDroppingToRenotifyLevel() {
        levels(99, 100)
        levels(97, 98, 99, 100)
        assertEquals(listOf("watch-1", "watch-1"), notified)
    }

    @Test
    fun tracksEachWatchIndependently() {
        levels(99, 100, identifier = watch)
        levels(99, 100, identifier = otherWatch)
        assertEquals(listOf("watch-1", "watch-2"), notified)
    }

    @Test
    fun nullLevelBreaksTheTransition() {
        levels(99, null, 100)
        assertEquals(emptyList(), notified)
    }

    @Test
    fun respectsConfigToggle() {
        config.value = CoreConfig(notifyWatchFullyCharged = false)
        levels(99, 100)
        assertEquals(emptyList(), notified)
    }
}
