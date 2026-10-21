package io.rebble.libpebblecommon.calls

import android.app.NotificationManager
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.util.PrivateLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallDoNotDisturbFilterTest {
    private var interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    private fun filter(
        config: NotificationConfig = NotificationConfig(respectDoNotDisturb = true),
    ) = CallDoNotDisturbFilter(
        notificationConfig = config.asFlow(),
        privateLogger = PrivateLogger(config.asFlow()),
        currentInterruptionFilter = { interruptionFilter },
    )

    @Test
    fun `setting off never suppresses calls`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter(NotificationConfig(respectDoNotDisturb = false))

        assertFalse(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }

    @Test
    fun `dnd off never suppresses calls`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL
        val filter = filter()

        assertFalse(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }

    @Test
    fun `dnd on suppresses when ranking says blocked`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "key",
            packageName = "com.android.dialer",
            title = "Alice",
            text = "+15551234567",
            matchesInterruptionFilter = false,
        )

        assertTrue(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }

    @Test
    fun `dnd on allows when ranking says allowed`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "key",
            packageName = "com.android.dialer",
            title = "Alice",
            text = "+15551234567",
            matchesInterruptionFilter = true,
        )

        assertFalse(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }

    @Test
    fun `dnd on allows when ranking arrives during wait`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "key",
            packageName = "com.android.dialer",
            title = "Alice",
            text = "+15551234567",
            matchesInterruptionFilter = null,
        )

        val decision = async {
            filter.shouldSuppressIncomingCall("Alice", "+15551234567")
        }
        advanceTimeBy(100)
        filter.updateCallRanking("key", matchesInterruptionFilter = true)

        assertFalse(decision.await())
    }

    @Test
    fun `dnd on suppresses when ranking is missing`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()

        assertTrue(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }

    @Test
    fun `ranking is consumed after call decision`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "key",
            packageName = "com.android.dialer",
            title = "Alice",
            text = "+15551234567",
            matchesInterruptionFilter = true,
        )

        assertFalse(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
        assertTrue(filter.shouldSuppressIncomingCall("Bob", "+15557654321"))
    }

    @Test
    fun `dnd on ignores fresh ranking from a different call`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "previous-key",
            packageName = "com.android.dialer",
            title = "Alice",
            text = "+15551234567",
            matchesInterruptionFilter = true,
        )

        assertTrue(filter.shouldSuppressIncomingCall("Bob", "+15557654321"))
    }

    @Test
    fun `dnd on allows when ranking phone number is formatted differently`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "key",
            packageName = "com.android.dialer",
            title = "Alice",
            text = "(555) 123-4567",
            matchesInterruptionFilter = true,
        )

        assertFalse(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }

    @Test
    fun `dnd on allows when ranking caller text includes a label`() = runTest {
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val filter = filter()
        filter.recordCallRanking(
            key = "key",
            packageName = "com.android.dialer",
            title = "Alice Mobile",
            text = "Incoming call",
            matchesInterruptionFilter = true,
        )

        assertFalse(filter.shouldSuppressIncomingCall("Alice", "+15551234567"))
    }
}
