package io.rebble.libpebblecommon.packets.blobdb

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineFlagSilentTest {
    @Test
    fun silentFlagIsWireBit6() {
        assertEquals(6, TimelineItem.Flag.SILENT.value)
        assertEquals(
            0x40u.toUShort(),
            TimelineItem.Flag.makeFlags(listOf(TimelineItem.Flag.SILENT)),
        )
    }
}
