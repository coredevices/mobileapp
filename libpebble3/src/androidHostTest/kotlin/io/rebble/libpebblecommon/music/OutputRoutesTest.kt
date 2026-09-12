package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.combineOutputRoutes
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
