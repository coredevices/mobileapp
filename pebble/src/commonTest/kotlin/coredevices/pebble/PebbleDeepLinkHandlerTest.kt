package coredevices.pebble

import com.eygraber.uri.Uri
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.parseAddStoreFeedFrom
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.parseTokenFrom
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.StoreAppLink
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.storeAppLink
import coredevices.pebble.services.PEBBLE_FEED_URL
import coredevices.pebble.services.REBBLE_FEED_URL
import io.ktor.http.encodeURLParameter
import coredevices.pebble.ui.PebbleNavBarRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PebbleDeepLinkHandlerTest {
    @Test fun handlesIosBootConfig() {
        val uri = Uri.parse("pebble://custom-boot-config-url/https%3A%2F%2Fboot.rebble.io%2Fapi%2Fstage2%2Fios%3Faccess_token%3DabcdefGHiJKLM01234567890OpQrST%26t%3D1744915159")
        val token = parseTokenFrom(uri.path)
        assertEquals("abcdefGHiJKLM01234567890OpQrST", token)
    }

    @Test fun handlesAndroidBootConfig() {
        val uri = Uri.parse("pebble://custom-boot-config-url/https%3A%2F%2Fboot.rebble.io%2Fapi%2Fstage2%2F%3Faccess_token%3DabcdefGHiJKLM01234567890OpQrST%26t%3D1742938502")
        val token = parseTokenFrom(uri.path)
        assertEquals("abcdefGHiJKLM01234567890OpQrST", token)
    }

    @Test fun parsesAddStoreFeed() {
        val uri = Uri.parse("pebble://add-store-feed/TTMM/https%3A%2F%2Fapps.ttmm.is%2Fpebble%2Fapi")
        assertEquals(
            PebbleNavBarRoutes.AppstoreSettingsRoute(
                addSourceName = "TTMM",
                addSourceUrl = "https://apps.ttmm.is/pebble/api",
            ),
            parseAddStoreFeedFrom(uri),
        )
    }

    @Test fun rejectsUnencodedAddStoreFeedUrl() {
        val uri = Uri.parse("pebble://add-store-feed/TTMM/https://apps.ttmm.is/pebble/api")
        assertNull(parseAddStoreFeedFrom(uri))
    }

    @Test fun rejectsAddStoreFeedWithoutUrl() {
        assertNull(parseAddStoreFeedFrom(Uri.parse("pebble://add-store-feed/TTMM")))
    }

    @Test fun recognisesStoreAppLinks() {
        val id = "52b231c2b70e1c159500009b"
        val pebble = StoreAppLink(PEBBLE_FEED_URL, id)
        val rebble = StoreAppLink(REBBLE_FEED_URL, id)
        assertEquals(pebble, storeAppLink("https://apps.repebble.com/91-dub-v4-0_$id"))
        assertEquals(pebble, storeAppLink("https://apps.repebble.com/$id?dev=1"))
        assertEquals(pebble, storeAppLink("https://apps.repebble.com/en_US/application/$id"))
        assertEquals(rebble, storeAppLink("https://apps.rebble.io/en_US/application/$id"))
        assertEquals(rebble, storeAppLink("pebble://appstore/$id"))
        assertEquals(pebble, storeAppLink("pebble://appstore/$id?source=${PEBBLE_FEED_URL.encodeURLParameter()}"))
    }

    @Test fun ignoresOtherLinks() {
        assertNull(storeAppLink("https://apps.repebble.com/faces"))
        assertNull(storeAppLink("https://apps.repebble.com/91-dub_52b231c2b70e1c159500009b/changelog"))
        assertNull(storeAppLink("https://example.com/91-dub_52b231c2b70e1c159500009b"))
        assertNull(storeAppLink("pebble://navbar/52b231c2b70e1c159500009b"))
    }
}
