package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.combineOutputRoutes
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.OutputRouteSelectionCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutputRoutesTest {
    private data class Route(
        val id: String,
        val deduplicationIds: Set<String> = emptySet(),
    )

    @Test
    fun selectedRouteComesFirstAndDuplicatesAreRemoved() {
        assertEquals(
            listOf("nest", "phone", "headphones"),
            combineOutputRoutes(
                selectedRoutes = listOf(Route("nest")),
                controllerRoutes = listOf(Route("phone"), Route("nest")),
                discoveredRoutes = listOf(Route("headphones"), Route("nest")),
                routeId = Route::id,
                routeDeduplicationIds = Route::deduplicationIds,
            ).map(Route::id),
        )
    }

    @Test
    fun physicalDeviceDuplicatesAreRemoved() {
        val selectedNest = Route("selected-nest", setOf("nest-device"))
        assertEquals(
            listOf(selectedNest),
            combineOutputRoutes(
                selectedRoutes = listOf(selectedNest),
                controllerRoutes = emptyList(),
                discoveredRoutes = listOf(Route("discovered-nest", setOf("nest-device"))),
                routeId = Route::id,
                routeDeduplicationIds = Route::deduplicationIds,
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
