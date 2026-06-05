package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexWebhookPreferencesTest {

    private val tokenKey = "index_webhook_auth_token"
    private val headersKey = "index_webhook_headers"

    @Test
    fun legacyTokenIsMigratedToWidgetTokenHeader() {
        val settings = MapSettings(tokenKey to "secret")

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(mapOf("X-Widget-Token" to "secret"), prefs.headers.value)
        // Legacy key is removed and the migration is persisted under the headers key.
        assertFalse(settings.hasKey(tokenKey))
        assertTrue(settings.hasKey(headersKey))
    }

    @Test
    fun blankLegacyTokenIsDiscardedWithoutCreatingHeader() {
        val settings = MapSettings(tokenKey to "   ")

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(emptyMap(), prefs.headers.value)
        assertFalse(settings.hasKey(tokenKey))
    }

    @Test
    fun existingHeadersAreNotOverwrittenByLegacyToken() {
        val settings = MapSettings(
            tokenKey to "secret",
            headersKey to """{"Authorization":"Bearer abc"}""",
        )

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(mapOf("Authorization" to "Bearer abc"), prefs.headers.value)
        assertFalse(settings.hasKey(tokenKey))
    }

    @Test
    fun headersRoundTripThroughSettings() {
        val settings = MapSettings()
        val headers = mapOf("X-Widget-Token" to "t", "X-Custom" to "v")

        IndexWebhookPreferences(settings).setHeaders(headers)

        // A fresh instance loads the persisted headers.
        assertEquals(headers, IndexWebhookPreferences(settings).headers.value)
    }

    @Test
    fun setEmptyHeadersRemovesPersistedValue() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        prefs.setHeaders(mapOf("X-A" to "1"))

        prefs.setHeaders(emptyMap())

        assertFalse(settings.hasKey(headersKey))
        assertEquals(emptyMap(), prefs.headers.value)
    }

    @Test
    fun clearAllRemovesHeaders() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        prefs.setWebhookUrl("https://example.com")
        prefs.setHeaders(mapOf("X-A" to "1"))

        prefs.clearAll()

        assertEquals(emptyMap(), prefs.headers.value)
        assertFalse(settings.hasKey(headersKey))
    }
}
