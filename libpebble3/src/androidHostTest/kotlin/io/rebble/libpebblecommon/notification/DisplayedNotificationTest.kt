package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.connection.endpointmanager.timeline.DisplayedItem
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.displayedItemAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DisplayedNotificationTest {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val a = Uuid.random()
    private val b = Uuid.random()

    private val history = listOf(
        DisplayedItem(t0, null),
        DisplayedItem(t0 + 10.seconds, a),
        DisplayedItem(t0 + 20.seconds, b),
        DisplayedItem(t0 + 30.seconds, null),
    )

    @Test
    fun theWatchReportsWhatIsOnScreen() {
        assertEquals(a, displayedItemAt(history, t0 + 15.seconds))
        assertEquals(b, displayedItemAt(history, t0 + 20.seconds))
    }

    @Test
    fun nothingOnScreenAfterTheWatchClosesTheNotification() {
        assertNull(displayedItemAt(history, t0 + 5.seconds))
        assertNull(displayedItemAt(history, t0 + 31.seconds))
    }

    @Test
    fun nothingKnownBeforeTheFirstReport() {
        assertNull(displayedItemAt(history, t0 - 1.seconds))
    }
}
