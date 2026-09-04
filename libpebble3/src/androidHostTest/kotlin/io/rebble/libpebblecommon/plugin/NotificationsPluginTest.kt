package io.rebble.libpebblecommon.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationsPluginTest {

    private fun shade(
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        title: CharSequence? = "Alice",
        text: CharSequence? = "Hello",
        bigText: CharSequence? = null,
    ) = shadeNotification(
        key = "0|com.example.app|1|null|10",
        packageName = "com.example.app",
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
        title = title,
        text = text,
        bigText = bigText,
        postedAt = 1_000L,
    )

    @Test
    fun ongoingNotificationsAreNotInTheShadeList() {
        assertNull(shade(isOngoing = true))
    }

    @Test
    fun groupSummariesAreDropped() {
        assertNull(shade(isGroupSummary = true))
    }

    @Test
    fun emptyNotificationsAreDropped() {
        assertNull(shade(title = null, text = null))
        assertNull(shade(title = "  ", text = ""))
    }

    @Test
    fun bigTextWinsOverText() {
        assertEquals("The whole message", shade(bigText = "The whole message")?.body)
    }

    @Test
    fun aTitlelessNotificationIsNamedByItsBody() {
        val result = shade(title = null, text = "Your parcel has arrived")
        assertEquals("Your parcel has arrived", result?.title)
        assertEquals("", result?.body)
    }

    @Test
    fun bidiIsolatesAreStripped() {
        assertEquals("Alice", shade(title = "\u2066Alice\u2069")?.title)
    }
}
