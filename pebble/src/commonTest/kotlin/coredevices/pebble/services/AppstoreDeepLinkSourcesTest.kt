package coredevices.pebble.services

import coredevices.database.AppstoreSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppstoreDeepLinkSourcesTest {
    private val custom = AppstoreSource(
        id = 9,
        url = "https://example.com/appstore/api",
        title = "Custom Store",
    )
    private val pebble = AppstoreSource(
        id = 2,
        url = PEBBLE_FEED_URL,
        title = "Pebble App Store",
    )
    private val rebble = AppstoreSource(
        id = 1,
        url = REBBLE_FEED_URL,
        title = "Rebble App Store",
    )

    @Test
    fun ordersBuiltInSourcesBeforeCustomSources() {
        val ordered = appstoreDeepLinkSources(
            sources = listOf(custom, rebble, pebble),
            sourceHint = null,
        )

        assertEquals(listOf(pebble, rebble, custom), ordered)
    }

    @Test
    fun pebbleHintOnlyReturnsPebbleSource() {
        val ordered = appstoreDeepLinkSources(
            sources = listOf(custom, rebble, pebble),
            sourceHint = "pebble",
        )

        assertEquals(listOf(pebble), ordered)
    }

    @Test
    fun rebbleHintOnlyReturnsRebbleSource() {
        val ordered = appstoreDeepLinkSources(
            sources = listOf(custom, rebble, pebble),
            sourceHint = "rebble",
        )

        assertEquals(listOf(rebble), ordered)
    }

    @Test
    fun exactUrlHintCanReturnCustomSource() {
        val ordered = appstoreDeepLinkSources(
            sources = listOf(custom, rebble, pebble),
            sourceHint = "${custom.url}/",
        )

        assertEquals(listOf(custom), ordered)
    }

    @Test
    fun unmatchedHintReturnsNoSources() {
        val ordered = appstoreDeepLinkSources(
            sources = listOf(custom, rebble, pebble),
            sourceHint = "missing",
        )

        assertTrue(ordered.isEmpty())
    }
}
