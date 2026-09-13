package coredevices.pebble

import android.os.Build
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PebbleAndroidDelegateTest {
    @Test
    fun mediaRoutingRequiresAndroid15() {
        assertFalse(
            mediaRoutingPermissionRequired(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                listOf(setOf(ProtocolCapsFlag.SupportsMusicOutputRouting)),
            )
        )
    }

    @Test
    fun mediaRoutingRequiresCompatibleWatch() {
        assertFalse(
            mediaRoutingPermissionRequired(
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                listOf(emptySet()),
            )
        )
        assertTrue(
            mediaRoutingPermissionRequired(
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                listOf(setOf(ProtocolCapsFlag.SupportsMusicOutputRouting)),
            )
        )
    }
}
