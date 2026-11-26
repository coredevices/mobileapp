package io.rebble.libpebblecommon.plugin

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The extras-parsing seam host tests can't reach: [toShadeNotification] on a real
 * [StatusBarNotification]. The public constructor is deprecated for apps ("non-system apps
 * should not need to create StatusBarNotifications"), which is exactly why this test only
 * runs on a device or emulator.
 */
class NotificationsOngoingDeviceTest {
    @Suppress("DEPRECATION")
    private fun shade(notification: Notification, sensitive: Boolean = false) =
        StatusBarNotification(
            "com.example.app",
            "com.example.app",
            1,
            null,
            10_000,
            0,
            0,
            notification,
            UserHandle.getUserHandleForUid(10_000),
            1_000L,
        )
            .toShadeNotification(sensitive = sensitive, wantOngoing = true)

    private fun ongoing(build: (Notification.Builder) -> Notification.Builder): Notification {
        val context = InstrumentationRegistry.getInstrumentation().context
        return build(Notification.Builder(context, "test-channel").setOngoing(true)).build()
    }

    @Test
    fun indeterminateProgressIsOmitted() {
        val result = shade(ongoing { it.setContentTitle("Working").setProgress(0, 100, true) })
        assertNotNull(result)
        assertNull(result?.progress)
        assertNull(result?.progressMax)
    }

    @Test
    fun determinateProgressIsExposed() {
        val result = shade(ongoing { it.setContentTitle("Downloading").setProgress(40, 100, false) })
        assertEquals(40, result?.progress)
        assertEquals(100, result?.progressMax)
    }

    @Test
    fun redactedNotificationDropsItsLargeIcon() {
        val icon = Icon.createWithBitmap(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
        val notification = ongoing { it.setContentTitle("Auth").setLargeIcon(icon) }

        assertNotNull(shade(notification)?.largeIcon)
        assertNull(shade(notification, sensitive = true)?.largeIcon)
    }
}
