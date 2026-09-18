package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.combineOutputRoutes
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.OutputRouteSelectionCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutputRoutesTest {
    @Test
    fun selectedRouteComesFirstAndDuplicatesAreRemoved() {
        assertEquals(
            listOf("Nest Mini", "Phone", "Headphones"),
            combineOutputRoutes(
                selectedRoutes = listOf("Nest Mini"),
                controllerRoutes = listOf("Phone", "Nest Mini"),
                discoveredRoutes = listOf("Headphones", "Nest Mini"),
                routeId = { it },
            ),
        )
    }

    @Test
    fun routeSnapshotsRemainIndependent() {
        val cache = OutputRouteSelectionCache<String>(2)
        val youtubeGeneration = cache.store("youtube", listOf("Phone", "Nest Mini"))
        val spotifyGeneration = cache.store("spotify", listOf("Phone", "Headphones"))

        assertEquals("Nest Mini", cache.route(youtubeGeneration, "youtube", 1u))
        assertEquals("Headphones", cache.route(spotifyGeneration, "spotify", 1u))
        assertNull(cache.route(youtubeGeneration, "spotify", 1u))
    }

    @Test
    fun oldestRouteSnapshotExpires() {
        val cache = OutputRouteSelectionCache<String>(1)
        val oldGeneration = cache.store("youtube", listOf("Phone"))
        cache.store("youtube", listOf("Nest Mini"))

        assertNull(cache.route(oldGeneration, "youtube", 0u))
    }
}
