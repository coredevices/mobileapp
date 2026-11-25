package coredevices.pebble.permissions

import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.perm_request_wants
import coreapp.pebble.generated.resources.perm_request_wants_review
import coreapp.pebble.generated.resources.perm_request_n_more
import coreapp.pebble.generated.resources.perm_request_wants_one
import coreapp.pebble.generated.resources.perm_request_wants_many
import coreapp.pebble.generated.resources.plugin_permission_list_and
import org.jetbrains.compose.resources.StringResource
import coredevices.pebble.permissions.PermissionRequestNotifier.Companion.requestBody
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionRequestNotifierTest {
    private val templates = mapOf(
        Res.string.perm_request_wants to "Wants to %1\$s",
        Res.string.perm_request_wants_review to "Wants to %1\$s. Review them in the Pebble app.",
        Res.string.perm_request_n_more to "%1\$d more",
        Res.string.perm_request_wants_one to "Wants a permission. Review it in the Pebble app.",
        Res.string.perm_request_wants_many to "Wants %1\$d permissions. Review them in the Pebble app.",
        Res.string.plugin_permission_list_and to "%1\$s and %2\$s",
    )

    private fun english(res: StringResource, args: Array<out Any>): String =
        args.foldIndexed(templates.getValue(res)) { i, text, arg -> text.replace("%${i + 1}\$s", "$arg").replace("%${i + 1}\$d", "$arg") }

    @Test
    fun aRequestThatFitsIsListedInFull() = runTest {
        assertEquals(
            "Wants to read your calendar, use your location and control music playback" to true,
            requestBody(string = ::english, labels = listOf("read your calendar", "use your location", "control music playback")),
        )
    }

    @Test
    fun aLongRequestListsWhatFitsThenHowManyMore() = runTest {
        val (body, complete) = requestBody(string = ::english, labels = List(30) { "connect to example$it.com" })
        assertFalse(complete)
        assertTrue(body.encodeToByteArray().size <= TimelineAttribute.Body.maxLength)
        assertTrue(body.startsWith("Wants to connect to example0.com, connect to example1.com,"))
        assertTrue(Regex(""" and \d+ more\. Review them in the Pebble app\.$""").containsMatchIn(body), body)
    }

    @Test
    fun aRequestWhoseFirstLabelDoesntFitIsCounted() = runTest {
        assertEquals(
            "Wants 2 permissions. Review them in the Pebble app." to false,
            requestBody(string = ::english, labels = listOf("x".repeat(600), "read your calendar")),
        )
    }

    @Test
    fun aSinglePermissionThatDoesntFitIsNotCounted() = runTest {
        assertEquals(
            "Wants a permission. Review it in the Pebble app." to false,
            requestBody(string = ::english, labels = listOf("x".repeat(600))),
        )
    }
}
