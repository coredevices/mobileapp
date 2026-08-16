package coredevices.ring.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class AiLocationContextTest {

    @Test
    fun coarsensToRoughly100mThreeDecimals() {
        // ~100m ≈ 3 decimal places (0.001° latitude ≈ 111m).
        assertEquals(37.775, coarsenCoordinate(37.7749295), 0.0)
        assertEquals(-122.419, coarsenCoordinate(-122.4194155), 0.0)
    }

    @Test
    fun formatsCoarseLocationLine() {
        assertEquals(
            "The user's approximate location is 37.775, -122.419 (latitude, longitude, accurate to ~100m). " +
                "Only use this if the request depends on location (e.g. weather or nearby places); otherwise ignore it.",
            locationContext(37.7749295, -122.4194155),
        )
    }
}
