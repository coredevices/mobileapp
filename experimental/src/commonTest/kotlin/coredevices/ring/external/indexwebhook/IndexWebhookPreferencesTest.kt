package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.MapSettings
import coredevices.ring.service.button.RingGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexWebhookPreferencesTest {

    private val urlKey = "index_webhook_url"
    private val tokenKey = "index_webhook_auth_token"
    private val headersKey = "index_webhook_headers"
    private val payloadModeKey = "index_webhook_payload_mode"
    private val triggerKey = "index_webhook_trigger"

    @Test
    fun recordingGesturesAreHoldAndClickHold() {
        assertEquals(
            listOf(RingGesture.Hold, RingGesture.ClickHold),
            IndexWebhookPreferences.gestures,
        )
    }

    @Test
    fun legacySingleConfigIsCopiedToEveryRecordingGesture() {
        val settings = MapSettings(
            urlKey to "https://example.com/hook",
            headersKey to """{"Authorization":"Bearer abc"}""",
            payloadModeKey to IndexWebhookPayloadMode.Both.id,
            // The old trigger only fired on double click and hold; both gestures still inherit it.
            triggerKey to 1,
        )

        val prefs = IndexWebhookPreferences(settings)

        val expected = IndexWebhookConfig(
            url = "https://example.com/hook",
            payloadMode = IndexWebhookPayloadMode.Both,
            headers = mapOf("Authorization" to "Bearer abc"),
            saved = true,
        )
        assertEquals(expected, prefs.configFor(RingGesture.Hold))
        assertEquals(expected, prefs.configFor(RingGesture.ClickHold))
        assertTrue(prefs.configFor(RingGesture.Hold).isActive)
    }

    @Test
    fun legacyKeysAreRemovedAndMigrationRunsOnlyOnce() {
        val settings = MapSettings(
            urlKey to "https://example.com/hook",
            tokenKey to "secret",
            payloadModeKey to IndexWebhookPayloadMode.TranscriptionOnly.id,
            triggerKey to 0,
        )

        IndexWebhookPreferences(settings)

        listOf(urlKey, tokenKey, headersKey, payloadModeKey, triggerKey).forEach {
            assertFalse(settings.hasKey(it), "legacy key $it should be gone")
        }
        val reloaded = IndexWebhookPreferences(settings)
        assertEquals("https://example.com/hook", reloaded.configFor(RingGesture.Hold).url)
        assertEquals(
            IndexWebhookPayloadMode.TranscriptionOnly,
            reloaded.configFor(RingGesture.ClickHold).payloadMode,
        )
    }

    @Test
    fun evenOlderAuthTokenBecomesAWidgetTokenHeader() {
        val settings = MapSettings(urlKey to "https://example.com/hook", tokenKey to "secret")

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(
            mapOf("X-Widget-Token" to "secret"),
            prefs.configFor(RingGesture.Hold).headers,
        )
    }

    @Test
    fun blankLegacyTokenIsDiscarded() {
        val settings = MapSettings(urlKey to "https://example.com/hook", tokenKey to "   ")

        val prefs = IndexWebhookPreferences(settings)

        assertEquals(emptyMap(), prefs.configFor(RingGesture.Hold).headers)
    }

    @Test
    fun noLegacyUrlLeavesEveryGestureUnconfigured() {
        val settings = MapSettings(triggerKey to 2)

        val prefs = IndexWebhookPreferences(settings)

        IndexWebhookPreferences.gestures.forEach {
            assertEquals(IndexWebhookConfig(), prefs.configFor(it))
            assertFalse(prefs.configFor(it).isActive)
        }
    }

    @Test
    fun configsAreStoredPerGestureAndRoundTrip() {
        val settings = MapSettings()
        val hold = IndexWebhookConfig(
            url = "https://hold.example.com",
            payloadMode = IndexWebhookPayloadMode.TranscriptionOnly,
            headers = mapOf("X-A" to "1"),
            saved = true,
        )

        IndexWebhookPreferences(settings).setConfig(RingGesture.Hold, hold)

        val reloaded = IndexWebhookPreferences(settings)
        assertEquals(hold, reloaded.configFor(RingGesture.Hold))
        assertEquals(IndexWebhookConfig(), reloaded.configFor(RingGesture.ClickHold))
    }

    @Test
    fun clearRemovesOnlyThatGesturesConfig() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        val config = IndexWebhookConfig(url = "https://example.com", saved = true)
        prefs.setConfig(RingGesture.Hold, config)
        prefs.setConfig(RingGesture.ClickHold, config)

        prefs.clear(RingGesture.Hold)

        assertEquals(IndexWebhookConfig(), prefs.configFor(RingGesture.Hold))
        assertEquals(config, prefs.configFor(RingGesture.ClickHold))
        assertEquals(config, IndexWebhookPreferences(settings).configFor(RingGesture.ClickHold))
    }

    @Test
    fun disablingKeepsUrlAndHeadersSoReEnablingRestoresThem() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        val config = IndexWebhookConfig(
            url = "https://example.com/hook",
            payloadMode = IndexWebhookPayloadMode.Both,
            headers = mapOf("X-A" to "1"),
            saved = true,
        )
        prefs.setConfig(RingGesture.Hold, config)

        prefs.setEnabled(RingGesture.Hold, false)

        assertFalse(prefs.configFor(RingGesture.Hold).isActive)
        assertEquals(config.copy(saved = false), prefs.configFor(RingGesture.Hold))
        assertEquals(
            config.copy(saved = false),
            IndexWebhookPreferences(settings).configFor(RingGesture.Hold),
        )

        prefs.setEnabled(RingGesture.Hold, true)

        assertTrue(prefs.configFor(RingGesture.Hold).isActive)
        assertEquals(config, prefs.configFor(RingGesture.Hold))
    }

    @Test
    fun settingEnabledWithoutAUrlKeepsHeadersAndStaysInactive() {
        val settings = MapSettings()
        val prefs = IndexWebhookPreferences(settings)
        val config = IndexWebhookConfig(url = "", headers = mapOf("X-A" to "1"), saved = true)
        prefs.setConfig(RingGesture.Hold, config)

        prefs.setEnabled(RingGesture.Hold, true)

        assertFalse(prefs.configFor(RingGesture.Hold).isActive)
        assertEquals(config.headers, prefs.configFor(RingGesture.Hold).headers)
    }

    @Test
    fun triggerKeyedConfigsAreRekeyedOntoTheirGesture() {
        val stored = """{"url":"https://example.com/hook","saved":true}"""
        val settings = MapSettings(
            "index_webhook_config_SingleClickHold" to stored,
            "index_webhook_config_DoubleClickHold" to stored,
        )

        val prefs = IndexWebhookPreferences(settings)

        assertEquals("https://example.com/hook", prefs.configFor(RingGesture.Hold).url)
        assertEquals("https://example.com/hook", prefs.configFor(RingGesture.ClickHold).url)
        assertTrue(prefs.configFor(RingGesture.Hold).isActive)
        assertEquals(null, settings.getStringOrNull("index_webhook_config_SingleClickHold"))
        assertEquals(null, settings.getStringOrNull("index_webhook_config_DoubleClickHold"))
    }

    @Test
    fun anExistingGestureConfigWinsOverATriggerKeyedOne() {
        val settings = MapSettings(
            "index_webhook_config_SingleClickHold" to """{"url":"https://old.example","saved":true}""",
            "index_webhook_config_Hold" to """{"url":"https://new.example","saved":true}""",
        )

        val prefs = IndexWebhookPreferences(settings)

        assertEquals("https://new.example", prefs.configFor(RingGesture.Hold).url)
    }

    @Test
    fun corruptStoredConfigFallsBackToUnconfigured() {
        val settings = MapSettings("index_webhook_config_Hold" to "not json")

        assertEquals(
            IndexWebhookConfig(),
            IndexWebhookPreferences(settings).configFor(RingGesture.Hold),
        )
    }
}
