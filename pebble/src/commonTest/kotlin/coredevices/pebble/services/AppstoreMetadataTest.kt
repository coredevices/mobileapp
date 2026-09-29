package coredevices.pebble.services

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppstoreMetadataTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun app(): StoreApplication = json.decodeFromString(APP_JSON)

    @Test
    fun publicRebbleMetadataIsPreservedWithoutALockerAccount() {
        val entry = assertNotNull(app().toLockerEntry(REBBLE_FEED_URL, timelineToken = null))

        // Values from a public Rebble response and its PBW headers. In particular,
        // emery's SDK must not be replaced with the legacy 5.86 fallback.
        assertEquals(listOf("basalt", "emery"), entry.hardwarePlatforms.map { it.name })
        assertEquals(listOf("5.86", "5.106"), entry.hardwarePlatforms.map { it.sdkVersion })
        assertEquals(listOf(137, 329), entry.hardwarePlatforms.map { it.pebbleProcessInfoFlags })
        val pbw = assertNotNull(entry.pbw)
        assertEquals(1, pbw.iconResourceId)
        assertEquals("https://example.com/app.pbw", pbw.file)
        assertEquals("1.2", entry.version)
        assertTrue(entry.isConfigurable)
        assertNull(entry.userToken)
    }

    @Test
    fun compatibilityFallbackDoesNotInventANativeBinary() {
        // The API can advertise compatibility via scaling without a native build.
        val original = app()
        val storeApp = original.copy(
            compatibility = original.compatibility.copy(
                chalk = original.compatibility.chalk.copy(supported = true)
            )
        )
        val entry = assertNotNull(storeApp.toLockerEntry(REBBLE_FEED_URL, timelineToken = null))

        assertTrue(entry.compatibility.chalk.supported)
        assertFalse(entry.hardwarePlatforms.any { it.name == "chalk" })
    }

    @Test
    fun releaseIdentityChangesEvenWhenVersionLabelStaysTheSame() {
        val storeApp = app()
        val first = assertNotNull(storeApp.toLockerEntry(REBBLE_FEED_URL, timelineToken = null))
        val updated = storeApp.copy(latestRelease = assertNotNull(storeApp.latestRelease).copy(id = "new-release"))
        val second = assertNotNull(updated.toLockerEntry(REBBLE_FEED_URL, timelineToken = null))

        assertEquals(first.version, second.version)
        assertEquals("release-id", assertNotNull(first.pbw).releaseId)
        assertEquals("new-release", assertNotNull(second.pbw).releaseId)
    }

    @Test
    fun existingTimelineTokenIsPreserved() {
        val entry = assertNotNull(app().toLockerEntry(REBBLE_FEED_URL, timelineToken = "existing-token"))

        assertEquals("existing-token", entry.userToken)
    }

    private companion object {
        // Minimal public store response: no locker-only user_token or pbw object.
        val APP_JSON = """
            {
                "id": "app-id",
                "uuid": "12345678-1234-1234-1234-123456789abc",
                "title": "Test watchface",
                "type": "watchface",
                "author": "Test developer",
                "developer_id": "developer-id",
                "capabilities": ["configurable"],
                "category": "Daily",
                "category_color": "000000",
                "category_id": "category-id",
                "changelog": [],
                "companions": {"android": null, "ios": null},
                "compatibility": {
                    "android": {"supported": true},
                    "ios": {"supported": true},
                    "aplite": {"supported": false, "firmware": {"major": 3}},
                    "basalt": {"supported": true, "firmware": {"major": 3}},
                    "chalk": {"supported": false, "firmware": {"major": 3}},
                    "diorite": {"supported": false, "firmware": {"major": 3}},
                    "emery": {"supported": true, "firmware": {"major": 3}}
                },
                "hardware_platforms": [
                    {"name": "basalt", "sdk_version": "5.86", "pebble_process_info_flags": 137,
                     "description": "Test", "images": {}},
                    {"name": "emery", "sdk_version": "5.106", "pebble_process_info_flags": 329,
                     "description": "Test", "images": {}}
                ],
                "icon_resource_id": 1,
                "created_at": "2026-09-30",
                "description": "Test",
                "header_images": "",
                "hearts": 0,
                "icon_image": {},
                "list_image": {},
                "latest_release": {
                    "id": "release-id", "version": "1.2",
                    "pbw_file": "https://example.com/app.pbw",
                    "js_md5": null, "js_version": -1,
                    "published_date": null, "release_notes": null
                },
                "links": {},
                "published_date": null,
                "screenshot_hardware": "basalt",
                "screenshot_images": [],
                "source": null,
                "visible": true,
                "website": null
            }
        """.trimIndent()
    }
}
