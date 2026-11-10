package io.rebble.libpebblecommon.metadata

import org.junit.Test
import kotlin.test.assertEquals

class WatchHardwarePlatformTest {
    @Test
    fun emulatorPlatforms() {
        assertEquals(WatchType.FLINT, WatchHardwarePlatform.fromProtocolNumber(0xf6u).watchType)
        assertEquals(WatchType.EMERY, WatchHardwarePlatform.fromProtocolNumber(0xf5u).watchType)
        assertEquals(WatchType.GABBRO, WatchHardwarePlatform.fromProtocolNumber(0xf2u).watchType)
    }
}
