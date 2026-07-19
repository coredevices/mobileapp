package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexWebhookPreferencesTest {

    private val tokenKey = "index_webhook_auth_token"
    private val flatUrlKey = "index_webhook_url"
    private val flatHeadersKey = "index_webhook_headers"
    private val flatPayloadModeKey = "index_webhook_payload_mode"
    private val flatTriggerKey = "index_webhook_trigger"

    private val singleUrlKey = "index_webhook_url_single_click_hold"
    private val singleHeadersKey = "index_webhook_headers_single_click_hold"
    private val doubleUrlKey = "index_webhook_url_double_click_hold"
    private val doubleHeadersKey = "index_webhook_headers_double_click_hold"

    private fun IndexWebhookPreferences.single() = config(IndexWebhookGesture.SingleClickHold).value
    private fun IndexWebhookPreferences.double() = config(IndexWebhookGesture.DoubleClickHold).value

    // Legacy trigger ids: 0 = SingleClick, 1 = DoubleClickHold, 2 = Both.

    @Test
    fun legacyTokenIsMigratedToWidgetTokenHeaderOnDefaultGesture() {
        val settings = MapSettings(tokenKey to "secret")

        val prefs = IndexWebhookPreferences(settings)

        // With no legacy trigger stored, the old default (double click & hold) receives the config.
        assertEquals(mapOf("X-Widget-Token" to "secret"), prefs.double().headers)
        assertEquals(emptyMap(), prefs.single().headers)
        assertFalse(settings.hasKey(tokenKey))
        assertFalse(settings.hasKey(flatHeadersKey))
        assertTrue(settings.hasKey(doubleHeadersKey))
    }

    @Test
    fun blankLegacyTokenIsDiscardedWithoutCreatingHeader() {
        val settings = MapSettings(tokenKey to "   ")

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(emptyMap(), prefs.single().headers)
        assertEquals(emptyMap(), prefs.double().headers)
        assertFalse(settings.hasKey(tokenKey))
    }

    @Test
    fun existingFlatHeadersAreNotOverwrittenByLegacyToken() {
        val settings = MapSettings(
            tokenKey to "secret",
            flatHeadersKey to """{"Authorization":"Bearer abc"}""",
        )

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(mapOf("Authorization" to "Bearer abc"), prefs.double().headers)
        assertFalse(settings.hasKey(tokenKey))
    }

    @Test
    fun flatConfigWithSingleClickTriggerSeedsOnlySingleGesture() {
        val settings = MapSettings(
            flatUrlKey to "https://example.com/hook",
            flatHeadersKey to """{"X-A":"1"}""",
            flatPayloadModeKey to IndexWebhookPayloadMode.Both.id,
            flatTriggerKey to 0,
        )

        val prefs = IndexWebhookPreferences(settings)

        val single = prefs.single()
        assertEquals("https://example.com/hook", single.url)
        assertEquals(mapOf("X-A" to "1"), single.headers)
        assertEquals(IndexWebhookPayloadMode.Both, single.payloadMode)
        assertFalse(prefs.double().isConfigured)
        assertFalse(settings.hasKey(flatUrlKey))
        assertFalse(settings.hasKey(flatHeadersKey))
        assertFalse(settings.hasKey(flatPayloadModeKey))
        assertFalse(settings.hasKey(flatTriggerKey))
    }

    @Test
    fun flatConfigWithBothTriggerSeedsBothGestures() {
        val settings = MapSettings(
            flatUrlKey to "https://example.com/hook",
            flatTriggerKey to 2,
        )

        val prefs = IndexWebhookPreferences(settings)

        assertEquals("https://example.com/hook", prefs.single().url)
        assertEquals("https://example.com/hook", prefs.double().url)
    }

    @Test
    fun flatConfigWithoutTriggerDefaultsToDoubleClickHold() {
        val settings = MapSettings(flatUrlKey to "https://example.com/hook")

        val prefs = IndexWebhookPreferences(settings)

        assertNull(prefs.single().url)
        assertEquals("https://example.com/hook", prefs.double().url)
    }

    @Test
    fun legacyTokenOnlyUserKeepsTokenThroughBothMigrations() {
        val settings = MapSettings(
            tokenKey to "secret",
            flatTriggerKey to 2,
        )

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(mapOf("X-Widget-Token" to "secret"), prefs.single().headers)
        assertEquals(mapOf("X-Widget-Token" to "secret"), prefs.double().headers)
    }

    @Test
    fun configRoundTripsThroughSettings() {
        val settings = MapSettings()
        val headers = mapOf("X-Widget-Token" to "t", "X-Custom" to "v")

        IndexWebhookPreferences(settings).apply {
            setWebhookUrl(IndexWebhookGesture.SingleClickHold, "https://example.com/hook")
            setHeaders(IndexWebhookGesture.SingleClickHold, headers)
            setPayloadMode(IndexWebhookGesture.SingleClickHold, IndexWebhookPayloadMode.TranscriptionOnly)
        }

        // A fresh instance loads the persisted config.
        val reloaded = IndexWebhookPreferences(settings).single()
        assertEquals("https://example.com/hook", reloaded.url)
        assertEquals(headers, reloaded.headers)
        assertEquals(IndexWebhookPayloadMode.TranscriptionOnly, reloaded.payloadMode)
    }

    @Test
    fun gestureConfigsAreIndependent() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)

        prefs.setWebhookUrl(IndexWebhookGesture.SingleClickHold, "https://single.example.com")
        prefs.setWebhookUrl(IndexWebhookGesture.DoubleClickHold, "https://double.example.com")

        assertEquals("https://single.example.com", prefs.single().url)
        assertEquals("https://double.example.com", prefs.double().url)
        assertTrue(settings.hasKey(singleUrlKey))
        assertTrue(settings.hasKey(doubleUrlKey))
    }

    @Test
    fun setEmptyHeadersRemovesPersistedValue() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        prefs.setHeaders(IndexWebhookGesture.SingleClickHold, mapOf("X-A" to "1"))

        prefs.setHeaders(IndexWebhookGesture.SingleClickHold, emptyMap())

        assertFalse(settings.hasKey(singleHeadersKey))
        assertEquals(emptyMap(), prefs.single().headers)
    }

    @Test
    fun clearRemovesOnlyThatGesturesConfig() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        prefs.setWebhookUrl(IndexWebhookGesture.SingleClickHold, "https://single.example.com")
        prefs.setHeaders(IndexWebhookGesture.SingleClickHold, mapOf("X-A" to "1"))
        prefs.setWebhookUrl(IndexWebhookGesture.DoubleClickHold, "https://double.example.com")

        prefs.clear(IndexWebhookGesture.SingleClickHold)

        assertFalse(prefs.single().isConfigured)
        assertEquals(emptyMap(), prefs.single().headers)
        assertFalse(settings.hasKey(singleUrlKey))
        assertFalse(settings.hasKey(singleHeadersKey))
        assertEquals("https://double.example.com", prefs.double().url)
    }

    @Test
    fun corruptHeadersJsonLoadsAsEmpty() {
        val settings = MapSettings(singleHeadersKey to "not json")

        assertEquals(emptyMap(), IndexWebhookPreferences(settings).single().headers)
    }
}
