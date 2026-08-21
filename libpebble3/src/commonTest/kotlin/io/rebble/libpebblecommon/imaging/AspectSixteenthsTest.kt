package io.rebble.libpebblecommon.imaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AspectSixteenthsTest {
    @Test
    fun squareIsSixteen() {
        assertEquals(16u, aspectSixteenths(400, 400))
    }

    @Test
    fun landscapeAndPortraitRound() {
        assertEquals(12u, aspectSixteenths(4000, 3000))
        assertEquals(21u, aspectSixteenths(3000, 4000))
        // 3:2 is 10.67 sixteenths, rounded not truncated
        assertEquals(11u, aspectSixteenths(3000, 2000))
    }

    @Test
    fun extremesClamp() {
        assertEquals(MIN_ASPECT_SIXTEENTHS.toUByte(), aspectSixteenths(2000, 100))
        assertEquals(MAX_ASPECT_SIXTEENTHS.toUByte(), aspectSixteenths(100, 2000))
    }

    @Test
    fun degenerateDimensionsHaveNoAspect() {
        assertNull(aspectSixteenths(0, 100))
        assertNull(aspectSixteenths(100, 0))
        assertNull(aspectSixteenths(-1, 100))
    }
}
