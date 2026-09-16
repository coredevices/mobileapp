package coredevices.pebble

import com.eygraber.uri.Uri
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.parseAddStoreFeedFrom
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.parseTokenFrom
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
}
