package coredevices.coreapp.ui.navigation

import CommonRoutes
import com.eygraber.uri.Uri
import coredevices.ring.ui.navigation.RingRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// handle() itself isn't exercised here because its fallback path constructs a
// NavUri (android.net.Uri on Android), which isn't available in JVM unit tests.
class CoreDeepLinkHandlerTest {
    private val handler = CoreDeepLinkHandler()

    private fun routeFor(uri: String) = handler.routeFor(Uri.parse(uri))

    @Test
    fun recordingDeepLinkParsesToRecordingDetails() {
        val route = assertIs<RingRoutes.RecordingDetails>(routeFor(RingRoutes.recordingDeepLink(123L)))
        assertEquals(123L, route.recordingId)
    }

    @Test
    fun recordingDeepLinkWithNonNumericIdDoesNotParse() {
        assertNull(routeFor("pebblecore://deep-link/recording?id=abc"))
    }

    @Test
    fun recordingDeepLinkWithoutIdDoesNotParse() {
        assertNull(routeFor("pebblecore://deep-link/recording"))
    }

    @Test
    fun objectDeepLinkParsesToObjectDetails() {
        val route = assertIs<RingRoutes.ObjectDetails>(routeFor(RingRoutes.objectDeepLink("firestore-doc-1")))
        assertEquals("firestore-doc-1", route.objectId)
    }

    @Test
    fun objectDeepLinkWithBlankIdDoesNotParse() {
        assertNull(routeFor("pebblecore://deep-link/object?id="))
    }

    @Test
    fun speechModelDeepLinkOpensTheDownloadDialog() {
        assertEquals(CommonRoutes.SpeechModelDownloadDialog, routeFor(CommonRoutes.SPEECH_MODEL_DOWNLOAD_DEEP_LINK))
    }

    @Test
    fun settingsDeepLinkOpensIndexSettings() {
        assertEquals(RingRoutes.Settings, routeFor(RingRoutes.SETTINGS_DEEP_LINK))
    }

    @Test
    fun timersAlarmsDeepLinkOpensTimersAndAlarms() {
        assertEquals(RingRoutes.TimersAndAlarms, routeFor(RingRoutes.TIMERS_ALARMS_DEEP_LINK))
    }

    @Test
    fun wrongSchemeHostOrPathDoesNotParse() {
        assertNull(routeFor("pebble://deep-link/index-settings"))
        assertNull(routeFor("pebblecore://other/index-settings"))
        assertNull(routeFor("pebblecore://deep-link/unknown"))
        assertNull(routeFor("pebblecore://deep-link/view-bug-report?conversationId=1"))
    }
}
