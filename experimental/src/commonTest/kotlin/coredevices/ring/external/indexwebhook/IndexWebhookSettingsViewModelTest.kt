package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.MapSettings
import coredevices.ring.service.button.RingGesture
import coredevices.util.integrations.IntegrationTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IndexWebhookSettingsViewModelTest {

    @Test
    fun saveStoresOnlyTheSigningFlagInPreferencesAndSecretInSecureStorage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fixture = Fixture()
            fixture.viewModel.openDialog(RingGesture.Hold)
            advanceUntilIdle()

            fixture.viewModel.updateUrlInput("https://example.com/hook")
            fixture.viewModel.updateSignRequests(true)
            fixture.viewModel.updateSigningSecret("  exact secret value  ")
            fixture.viewModel.save()
            advanceUntilIdle()

            val config = fixture.preferences.configFor(RingGesture.Hold)
            assertTrue(config.signRequests)
            assertEquals("https://example.com/hook", config.url)
            assertEquals(
                "  exact secret value  ",
                fixture.secretStorage.get(RingGesture.Hold),
            )
            assertFalse(fixture.settings.keys.any { key ->
                fixture.settings.getStringOrNull(key)?.contains("exact secret value") == true
            })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun copyAndTestEventUseTheOtherGesturesSigningSecret() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fixture = Fixture()
            fixture.preferences.setConfig(
                RingGesture.ClickHold,
                IndexWebhookConfig(
                    url = "https://other.example/hook",
                    signRequests = true,
                    saved = true,
                ),
            )
            fixture.secretStorage.save(RingGesture.ClickHold, "other-secret")

            fixture.viewModel.openDialog(RingGesture.Hold)
            advanceUntilIdle()
            fixture.viewModel.copyFromOtherGesture()
            advanceUntilIdle()

            assertEquals("https://other.example/hook", fixture.viewModel.urlInput.value)
            assertTrue(fixture.viewModel.signRequestsInput.value)
            assertEquals("other-secret", fixture.viewModel.signingSecretInput.value)

            fixture.viewModel.sendTestEvent()
            advanceUntilIdle()

            assertEquals(
                TestRequest(
                    gesture = RingGesture.Hold,
                    url = "https://other.example/hook",
                    signRequests = true,
                    signingSecret = "other-secret",
                ),
                fixture.api.lastTestRequest,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removingWebhookAlsoDeletesItsSigningSecret() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fixture = Fixture()
            fixture.preferences.setConfig(
                RingGesture.Hold,
                IndexWebhookConfig(
                    url = "https://example.com/hook",
                    signRequests = true,
                    saved = true,
                ),
            )
            fixture.secretStorage.save(RingGesture.Hold, "secret")

            fixture.viewModel.openDialog(RingGesture.Hold)
            advanceUntilIdle()
            fixture.viewModel.removeCurrentGesture()
            advanceUntilIdle()

            assertEquals(IndexWebhookConfig(), fixture.preferences.configFor(RingGesture.Hold))
            assertNull(fixture.secretStorage.get(RingGesture.Hold))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Fixture {
        val settings = MapSettings()
        val preferences = IndexWebhookPreferences(settings)
        private val tokenStorage = MemoryTokenStorage()
        val secretStorage = IndexWebhookSigningSecretStorage(tokenStorage)
        val api = FakeWebhookApi()
        private val runRepository = IndexWebhookRunRepository(settings)
        val viewModel = IndexWebhookSettingsViewModel(
            preferences,
            api,
            runRepository,
            secretStorage,
        )
    }

    private data class TestRequest(
        val gesture: RingGesture,
        val url: String,
        val signRequests: Boolean,
        val signingSecret: String?,
    )

    private class FakeWebhookApi : IndexWebhookApi {
        var lastTestRequest: TestRequest? = null

        override fun uploadIfEnabled(
            samples: ShortArray?,
            sampleRate: Int,
            recordingId: String,
            transcription: String?,
            recordedAt: Instant,
            gesture: RingGesture,
        ) = Unit

        override suspend fun sendTestEvent(
            gesture: RingGesture,
            url: String,
            headers: Map<String, String>,
            signRequests: Boolean,
            signingSecret: String?,
        ): IndexWebhookRunResult {
            lastTestRequest = TestRequest(gesture, url, signRequests, signingSecret)
            return IndexWebhookRunResult(true, "200 OK", "test event", 0, 0)
        }
    }

    private class MemoryTokenStorage : IntegrationTokenStorage {
        private val values = mutableMapOf<String, String>()

        override suspend fun saveToken(key: String, token: String) {
            values[key] = token
        }

        override suspend fun getToken(key: String): String? = values[key]

        override suspend fun deleteToken(key: String) {
            values.remove(key)
        }
    }
}
