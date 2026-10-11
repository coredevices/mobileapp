package coredevices.ring.agent.confirmation

import com.eygraber.uri.Uri
import coredevices.mcp.data.SemanticResult
import coredevices.ring.ui.navigation.RingRoutes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentUserConfirmationTest {

    private val request = SemanticResult.RequiresConfirmation(
        integrationName = "Music",
        toolName = "clear_queue",
        arguments = JsonObject(mapOf("all" to JsonPrimitive(true))),
        destructive = true,
        description = "Empty the play queue.",
    )

    @Test
    fun deepLinkRoundTrips() {
        val link = confirmationDeepLink(recordingId = 42, toolCallId = "call_1/abc=", approved = false)

        assertEquals(ConfirmationResponse(42, "call_1/abc=", approved = false), parseConfirmationDeepLink(Uri.parse(link)))
    }

    @Test
    fun malformedDeepLinksAreRejected() {
        assertNull(parseConfirmationDeepLink(Uri.parse("pebblecore://index-link/confirm-tool?recordingId=1&callId=c")))
        assertNull(parseConfirmationDeepLink(Uri.parse("pebblecore://index-link/confirm-tool?recordingId=x&callId=c&approved=true")))
        assertNull(parseConfirmationDeepLink(Uri.parse("pebblecore://index-link/send-to-me?recordingId=1")))
    }

    @Test
    fun notificationOpensTheRecordingAndOffersAllowAndDeny() {
        val notification = confirmationNotification(recordingId = 7, toolCallId = "c1", request)

        assertEquals(RingRoutes.recordingDeepLink(7), notification.deepLink)
        assertEquals(listOf("Allow", "Deny"), notification.actions.map { it.title })
        assertEquals(
            listOf(true, false),
            notification.actions.map { parseConfirmationDeepLink(Uri.parse(it.deepLink))?.approved },
        )
        assertTrue(notification.actions.all { it.background })
        val content = notification.contentText.orEmpty()
        assertContains(content, "Music")
        assertContains(content, "Clear queue")
        assertContains(content, "Empty the play queue.")
        assertContains(content, "destructive")
        assertEquals(confirmationNotificationId("c1"), notification.id)
    }
}
