package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val REVIEWED_APP = Uuid.parse("00000000-0000-0000-0003-000000000001")

private fun parse(json: String) = Json.decodeFromString(ListSerializer(PluginPermission.serializer()), json)

class PermissionDeclarationsTest {
    @Test
    fun reasonAndRequiredAreRead() {
        val parsed = parse(
            """["LocalNetwork", {"name": "Location", "reason": "Shows the weather where you are", "required": true}]"""
        )
        assertEquals(PluginPermission(PluginPermissions.LOCAL_NETWORK), parsed[0])
        assertEquals("Shows the weather where you are", parsed[1].reason)
        assertEquals(true, parsed[1].required)
        assertNull(parsed[0].required)
    }

    @Test
    fun aBlankReasonIsNone() {
        assertNull(parse("""[{"name": "Location", "reason": "  "}]""").single().reason)
    }

    @Test
    fun keysMapBackToTheirDeclaration() {
        val internet = PluginPermission(
            PluginPermissions.INTERNET,
            mapOf(PluginPermissions.PARAM_DOMAINS to listOf("api.example.com")),
            reason = "Fetches your scores",
        )
        val files = PluginPermission(PluginPermissions.READ_FILES, reason = "Reads your notes", required = true)
        val declared = listOf(internet, files)
        assertEquals(internet, declared.declarationOf(internetKey("api.example.com")))
        // A file the user picked is granted under its own key, but declared as the bare permission.
        assertEquals(files, declared.declarationOf("${PluginPermissions.READ_FILES}/notes"))
        assertNull(declared.declarationOf(PluginPermissions.LOCATION))
    }

    @Test
    fun onlyRequiredPermissionsNeedAnAnswerBeforeInstalling() {
        val review = InstallReview(
            appUuid = REVIEWED_APP,
            appName = "App",
            toReview = listOf(PluginPermissions.LOCATION, PluginPermissions.TIMELINE),
            declared = listOf(
                PluginPermission(PluginPermissions.LOCATION, required = true),
                PluginPermission(PluginPermissions.TIMELINE),
            ),
        )
        assertTrue(review.needsAnswer(PluginPermissions.LOCATION))
        assertFalse(review.needsAnswer(PluginPermissions.TIMELINE))
    }

    @Test
    fun withNothingSaidAboutRequiredEverythingNeedsAnAnswerAndNothingIsRequired() {
        val review = InstallReview(
            REVIEWED_APP,
            "App",
            toReview = listOf(PluginPermissions.LOCATION),
            declared = listOf(PluginPermission(PluginPermissions.LOCATION, reason = "Weather")),
        )
        assertTrue(review.needsAnswer(PluginPermissions.LOCATION))
        assertFalse(review.isRequired(PluginPermissions.LOCATION))
    }

    @Test
    fun onceRequiredIsSaidAnythingUnmarkedIsOptional() {
        val declared = listOf(
            PluginPermission(PluginPermissions.LOCATION, required = true),
            PluginPermission(PluginPermissions.TIMELINE),
        )
        assertTrue(declared.isRequired(PluginPermissions.LOCATION))
        assertFalse(declared.isRequired(PluginPermissions.TIMELINE))
    }

    @Test
    fun aLegacyAppsReviewAllNeedsAnswers() {
        val review = InstallReview(REVIEWED_APP, "App", toReview = listOf(PluginPermissions.GLOBAL_INTERNET), declared = null)
        assertTrue(review.needsAnswer(PluginPermissions.GLOBAL_INTERNET))
        assertFalse(review.isRequired(PluginPermissions.GLOBAL_INTERNET))
    }
}
