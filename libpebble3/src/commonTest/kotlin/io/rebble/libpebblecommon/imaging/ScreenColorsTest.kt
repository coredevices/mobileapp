package io.rebble.libpebblecommon.imaging

import kotlin.test.Test
import kotlin.test.assertEquals

internal class ScreenColorsTest {
    @Test
    fun panelBlackAndWhiteNormalizeToTheEndsOfTheRange() {
        assertEquals(listOf(0, 0, 0), listOf(SCREEN_R[0], SCREEN_G[0], SCREEN_B[0]))
        assertEquals(listOf(255, 255, 255), listOf(SCREEN_R[63], SCREEN_G[63], SCREEN_B[63]))
        assertEquals(0, nearestScreenColor(0, 0, 0))
        assertEquals(63, nearestScreenColor(255, 255, 255))
    }

    @Test
    fun saturatedColorsMatchAwayFromTheirNominalCode() {
        // The panel's red is far dimmer than sRGB's, so full red is nearer 0xE0 than the 0xF0 its
        // 2-bit quantisation would pick. Matching on nominal values is what washed images out.
        assertEquals(0x20, nearestScreenColor(255, 0, 0))
        assertEquals(0x15, nearestScreenColor(128, 128, 128))
    }
}
