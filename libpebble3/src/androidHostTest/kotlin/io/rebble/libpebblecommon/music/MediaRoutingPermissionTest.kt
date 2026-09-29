package io.rebble.libpebblecommon.music

import android.app.AppOpsManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaRoutingPermissionTest {
    @Test
    fun allowedAppOpGrantsAccessWithoutRegularPermission() {
        assertTrue(isMediaRoutingControlGranted(AppOpsManager.MODE_ALLOWED, false))
    }

    @Test
    fun defaultAppOpUsesRegularPermission() {
        assertTrue(isMediaRoutingControlGranted(AppOpsManager.MODE_DEFAULT, true))
        assertFalse(isMediaRoutingControlGranted(AppOpsManager.MODE_DEFAULT, false))
    }

    @Test
    fun deniedAppOpOverridesRegularPermission() {
        assertFalse(isMediaRoutingControlGranted(AppOpsManager.MODE_IGNORED, true))
    }
}
