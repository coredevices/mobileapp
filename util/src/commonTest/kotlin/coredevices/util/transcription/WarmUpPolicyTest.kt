package coredevices.util.transcription

import coredevices.util.models.CactusSTTMode
import io.rebble.libpebblecommon.plugin.Connections
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarmUpPolicyTest {

    private fun warmsUp(mode: CactusSTTMode, connection: String?) =
        HybridTranscriptionService.shouldWarmUpLocalModel(mode, connection)

    @Test
    fun localPreferringModesAlwaysWarmUp() {
        for (mode in listOf(CactusSTTMode.LocalOnly, CactusSTTMode.LocalFirst)) {
            assertTrue(warmsUp(mode, Connections.WIFI), "$mode on Wi-Fi")
            assertTrue(warmsUp(mode, Connections.NONE), "$mode offline")
            assertTrue(warmsUp(mode, null), "$mode unknown")
        }
    }

    @Test
    fun remoteFirstWarmsUpOnlyWhenOffline() {
        assertTrue(warmsUp(CactusSTTMode.RemoteFirst, Connections.NONE))
        assertFalse(warmsUp(CactusSTTMode.RemoteFirst, Connections.WIFI))
        assertFalse(warmsUp(CactusSTTMode.RemoteFirst, Connections.CELLULAR))
        assertFalse(warmsUp(CactusSTTMode.RemoteFirst, null))
    }

    @Test
    fun rebbleFallbackModesFollowTheRemoteFirstRule() {
        for (mode in listOf(CactusSTTMode.RebbleFirst, CactusSTTMode.RebbleFallback)) {
            assertTrue(warmsUp(mode, Connections.NONE), "$mode offline")
            assertFalse(warmsUp(mode, Connections.WIFI), "$mode on Wi-Fi")
        }
    }

    @Test
    fun modesWithoutLocalCactusNeverWarmUp() {
        for (mode in listOf(CactusSTTMode.RemoteOnly, CactusSTTMode.PlatformOnly, CactusSTTMode.RebbleOnly)) {
            assertFalse(warmsUp(mode, Connections.NONE), "$mode offline")
            assertFalse(warmsUp(mode, Connections.WIFI), "$mode on Wi-Fi")
        }
    }
}
