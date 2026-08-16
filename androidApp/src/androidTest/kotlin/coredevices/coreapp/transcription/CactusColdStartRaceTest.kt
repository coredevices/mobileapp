package coredevices.coreapp.transcription

import androidx.test.platform.app.InstrumentationRegistry
import coredevices.coreapp.testsupport.NoopAnalytics
import coredevices.coreapp.testsupport.ReadOnlyModelPathProvider
import coredevices.ring.model.CactusModelProvider
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.STTConfig
import coredevices.util.models.CactusSTTMode
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.NoOpInferenceBoost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * On-device regression guard for the cold-start local-STT wedge (MOB-9812 / MOB-9711).
 *
 * The bug: on the first note after an idle gap, [CactusTranscriptionService] warms the model up with
 * silent audio; the warm-up and the real transcription touched the *same* native `modelHandle` under
 * two uncoordinated mutexes, and the warm-up's 2 s `withTimeout` could `cactusStop` that shared handle
 * mid-transcription — wedging it so every subsequent recording came back blank ("No speech detected").
 *
 * The fix serializes all native-handle access on a single `modelMutex`: `transcribeLocal` holds it for
 * the whole native call, and `warmUpIfIdle` uses `tryLock` so it yields to an in-flight transcription
 * instead of stopping it. These tests drive the exact cold-start warm-up↔transcribe interleaving on the
 * REAL parakeet engine and assert that a known-speech clip always transcribes — i.e. the handle never
 * wedges. A regression to two-mutex / shared-handle behaviour would make a run come back blank and fail.
 *
 * Runs the real native model (downloaded on demand). Run against a persistent install so the model
 * isn't wiped between runs:
 *   adb shell am instrument -w \
 *     -e class coredevices.coreapp.transcription.CactusColdStartRaceTest \
 *     coredevices.coreapp.test/androidx.test.runner.AndroidJUnitRunner
 */
class CactusColdStartRaceTest {
    private companion object {
        const val MODEL_NAME = "parakeet-tdt-0.6b-v3"
        const val SAMPLE_RATE = 16_000

        // A real speech clip that transcribes reliably (raw PCM 16k/mono/s16 in test assets). The wedge
        // manifests as a *blank* result, so a non-blank transcription containing this distinctive word
        // proves the handle is healthy. (Local parakeet renders this clip "...cornstarch to my shopping list.")
        const val CLIP_ASSET = "eval_shopping_list_shrimp.raw"
        const val CLIP_KEYWORD = "cornstarch"

        // The race is timing-dependent, so hammer it. Each iteration re-arms the cold-start warm-up and
        // races it against a real transcription; a reintroduced wedge would trip at least one iteration.
        const val RACE_ITERATIONS = 25
        const val COLD_START_ITERATIONS = 8

        private val initLock = Any()
        private var sharedService: CactusTranscriptionService? = null
        private var modelPresent = false
        private var clip: ByteArray = ByteArray(0)
    }

    private lateinit var service: CactusTranscriptionService

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        synchronized(initLock) {
            if (sharedService == null) {
                // Assets live in the test APK, so read them from the instrumentation context.
                clip = InstrumentationRegistry.getInstrumentation().context.assets
                    .open(CLIP_ASSET).use { it.readBytes() }

                val modelsDir = File(context.filesDir, "models")
                val provider = ReadOnlyModelPathProvider(modelsDir, MODEL_NAME)
                if (!provider.isModelDownloaded(MODEL_NAME)) {
                    println("[cold-start] model missing — downloading $MODEL_NAME (one-time)…")
                    runBlocking { withTimeout(20.minutes) { CactusModelProvider().getSTTModelPath() } }
                }
                modelPresent = provider.isModelDownloaded(MODEL_NAME)

                if (modelPresent) {
                    val svc = CactusTranscriptionService(
                        coreConfigFlow = CoreConfigFlow(
                            MutableStateFlow(
                                CoreConfig(sttConfig = STTConfig(mode = CactusSTTMode.LocalOnly, modelName = MODEL_NAME)),
                            ),
                        ),
                        modelProvider = provider,
                        analytics = NoopAnalytics,
                        inferenceBoost = NoOpInferenceBoost(),
                    )
                    runBlocking {
                        svc.earlyInit()
                        withTimeout(2.minutes) { while (!svc.isModelReady) delay(200) }
                    }
                    sharedService = svc
                }
            }
        }
        Assume.assumeTrue("STT model '$MODEL_NAME' unavailable (download failed?)", modelPresent)
        service = sharedService!!
    }

    /** Force the "first note after an idle gap" condition so the next warm-up actually runs. */
    private fun forceColdStart() {
        val field = CactusTranscriptionService::class.java.getDeclaredField("lastTranscriptionAt")
        field.isAccessible = true
        field.set(service, null)
    }

    private suspend fun transcribeClip(timeout: Duration = 30.seconds): String =
        service.transcribeLocal(audio = clip, sampleRate = SAMPLE_RATE, timeout = timeout)

    private fun assertHealthy(text: String, where: String) {
        assertTrue(
            text.isNotBlank(),
            "[$where] transcription came back blank — the cold-start warm-up wedged the model handle " +
                "(MOB-9812/9711 regression).",
        )
        assertTrue(
            text.lowercase().contains(CLIP_KEYWORD),
            "[$where] transcription '$text' is missing the expected word '$CLIP_KEYWORD' — the handle " +
                "produced corrupted output.",
        )
    }

    /** Baseline: a plain cold-start transcription must produce real text, not blank. */
    @Test
    fun coldStart_transcribesRealSpeech() = runBlocking(Dispatchers.Default) {
        forceColdStart()
        assertHealthy(transcribeClip(), "cold-start")
    }

    /**
     * The core guard: repeatedly re-arm the cold-start warm-up and race it against a real transcription.
     * Every transcription must succeed — with the pre-fix two-mutex split, the warm-up's `cactusStop`
     * could wedge the shared handle and blank out the transcription.
     */
    @Test
    fun warmupRacingTranscription_neverWedges() = runBlocking(Dispatchers.Default) {
        repeat(RACE_ITERATIONS) { i ->
            forceColdStart()
            // Kick the warm-up (silent-audio inference on the shared handle) in the service's own scope…
            val warmup = launch { service.earlyInit() }
            // …and immediately run a real transcription so the two contend for the model handle.
            val text = transcribeClip()
            warmup.join()
            assertHealthy(text, "race #$i")
        }
    }

    /**
     * The reported symptom was that once wedged, *every* later note stayed blank. Simulate a series of
     * spaced-out first-notes-of-the-day (each re-arms warm-up) and assert none of them wedges.
     */
    @Test
    fun repeatedColdStarts_allTranscribe() = runBlocking(Dispatchers.Default) {
        repeat(COLD_START_ITERATIONS) { i ->
            forceColdStart()
            service.earlyInit() // fire-and-forget warm-up, as on a real cold start
            delay(50)
            assertHealthy(transcribeClip(), "cold-start #$i")
        }
    }

}
