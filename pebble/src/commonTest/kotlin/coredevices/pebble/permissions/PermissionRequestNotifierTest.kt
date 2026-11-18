package coredevices.pebble.permissions

import coredevices.pebble.permissions.PermissionRequestNotifier.Companion.requestBody
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionRequestNotifierTest {
    @Test
    fun aRequestThatFitsIsListedInFull() {
        assertEquals(
            "Wants to read your calendar, use your location and control music playback" to true,
            requestBody(listOf("read your calendar", "use your location", "control music playback")),
        )
    }

    @Test
    fun aLongRequestListsWhatFitsThenHowManyMore() {
        val (body, complete) = requestBody(List(30) { "connect to example$it.com" })
        assertFalse(complete)
        assertTrue(body.encodeToByteArray().size <= TimelineAttribute.Body.maxLength)
        assertTrue(body.startsWith("Wants to connect to example0.com, connect to example1.com,"))
        assertTrue(Regex(""" and \d+ more\. Review them in the Pebble app\.$""").containsMatchIn(body), body)
    }

    @Test
    fun aRequestWhoseFirstLabelDoesntFitIsCounted() {
        assertEquals(
            "Wants 2 permissions. Review them in the Pebble app." to false,
            requestBody(listOf("x".repeat(600), "read your calendar")),
        )
    }

    @Test
    fun aSinglePermissionThatDoesntFitIsNotCounted() {
        assertEquals(
            "Wants a permission. Review it in the Pebble app." to false,
            requestBody(listOf("x".repeat(600))),
        )
    }
}
