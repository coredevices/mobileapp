package coredevices.coreapp.ui.navigation

import com.eygraber.uri.Uri
import coredevices.ring.ui.navigation.RingRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// handle() itself isn't exercised here because its fallback path constructs a
// NavUri (android.net.Uri on Android), which isn't available in JVM unit tests.
class CoreDeepLinkHandlerTest {
    private val handler = CoreDeepLinkHandler()

    @Test
    fun recordingDeepLinkParsesToRecordingDetails() {
        val route = handler.recordingRouteFor(Uri.parse(RingRoutes.recordingDeepLink(123L)))
        assertNotNull(route)
        assertEquals(123L, route.recordingId)
    }

    @Test
    fun recordingDeepLinkWithNonNumericIdDoesNotParse() {
        assertNull(handler.recordingRouteFor(Uri.parse("pebblecore://deep-link/recording?id=abc")))
    }

    @Test
    fun recordingDeepLinkWithoutIdDoesNotParse() {
        assertNull(handler.recordingRouteFor(Uri.parse("pebblecore://deep-link/recording")))
    }

    @Test
    fun recordingDeepLinkWithWrongSchemeOrPathDoesNotParse() {
        assertNull(handler.recordingRouteFor(Uri.parse("pebble://deep-link/recording?id=123")))
        assertNull(handler.recordingRouteFor(Uri.parse("pebblecore://deep-link/object?id=123")))
    }

    @Test
    fun objectDeepLinkParsesToObjectDetails() {
        val route = handler.objectRouteFor(Uri.parse(RingRoutes.objectDeepLink("firestore-doc-1")))
        assertNotNull(route)
        assertEquals("firestore-doc-1", route.objectId)
    }

    @Test
    fun objectDeepLinkWithBlankIdDoesNotParse() {
        assertNull(handler.objectRouteFor(Uri.parse("pebblecore://deep-link/object?id=")))
    }
}
