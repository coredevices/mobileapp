package coredevices.pebble.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseCoordinateTest {
    @Test
    fun parsesValidCoordinates() {
        assertEquals(51.5074, parseCoordinate("51.5074", 90.0))
        assertEquals(-0.1278, parseCoordinate(" -0.1278 ", 180.0))
        assertEquals(-122.4, parseCoordinate("-122,4", 180.0))
        assertEquals(90.0, parseCoordinate("90", 90.0))
    }

    @Test
    fun rejectsInvalidCoordinates() {
        assertNull(parseCoordinate("", 90.0))
        assertNull(parseCoordinate("abc", 90.0))
        assertNull(parseCoordinate("90.1", 90.0))
        assertNull(parseCoordinate("-180.5", 180.0))
    }
}
