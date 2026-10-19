package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.ongoingIsShown
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OngoingNotificationTest {

    @Test
    fun shownForAnAppThatOptsIn() {
        assertTrue(ongoingIsShown("com.anthropic.claude", isForegroundService = false))
    }

    @Test
    fun notShownForOtherApps() {
        assertFalse(ongoingIsShown("com.example.vpn", isForegroundService = false))
    }

    @Test
    fun neverShownForAForegroundService() {
        assertFalse(ongoingIsShown("com.anthropic.claude", isForegroundService = true))
    }
}
