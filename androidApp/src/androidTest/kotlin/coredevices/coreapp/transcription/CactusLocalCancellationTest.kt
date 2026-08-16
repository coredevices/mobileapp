package coredevices.coreapp.transcription

import androidx.test.platform.app.InstrumentationRegistry
import coredevices.analytics.CoreAnalytics
import coredevices.ring.model.CactusModelProvider
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.STTConfig
import coredevices.util.models.CactusSTTMode
import coredevices.util.transcription.CactusModelPathProvider
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.NoOpInferenceBoost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.sin
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Instrumented diagnostics for local Cactus transcription cancellation. Runs the *real* native
 * model on-device, so it answers the question "does cancelling the coroutine actually stop the
 * native inference, and how promptly?" — which is what the [CactusTranscriptionService]
 * cancellation wiring (withCactusStopOnCancel -> cactusStop) relies on.
 *
 * The model is loaded once and shared across the tests (a fresh service per test re-runs
 * cactusInit, which can be very slow after eviction and skews timings).
 *
 * If the model isn't present it's downloaded on demand (one-time, large). Note: `gradle
 * connectedAndroidTest` uninstalls the app afterwards, wiping the model, so it re-downloads each
 * run — run via `adb shell am instrument` against a persistent install to avoid that:
 *   adb shell am instrument -w \
 *     -e class coredevices.coreapp.transcription.CactusLocalCancellationTest \
 *     coredevices.coreapp.test/androidx.test.runner.AndroidJUnitRunner
 */
class CactusLocalCancellationTest {
    private companion object {
        const val MODEL_NAME = "parakeet-tdt-0.6b-v3"
        const val SAMPLE_RATE = 16_000

        // Long enough that baseline inference (~tens of seconds) is far larger than BUDGET +
        // MAX_UNWIND, so "did the budget bound it?" is unambiguous. Pure tone; content is irrelevant.
        val AUDIO_DURATION = 300.seconds

        // How long we let inference run before cancelling / the phone-side budget under test.
        val PRE_CANCEL_DELAY = 3.seconds
        val BUDGET = 4.seconds

        // A cooperative native stop should unwind well within this. If it doesn't, cactusStop is
        // not being honoured by the native decode loop (the bug we're hunting).
        val MAX_UNWIND = 5.seconds

        private val initLock = Any()
        private var sharedService: CactusTranscriptionService? = null
        private var modelPresent = false
    }

    private lateinit var service: CactusTranscriptionService

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Use the running app's Koin graph (MainApplication started it). Do NOT stop/replace it: the
        // live app (PebbleService, Ring scanning, its uncaught-exception handler) depends on that
        // graph. WisprFlow/Kirinki resolve their HttpClientEngine from it.
        synchronized(initLock) {
            if (sharedService == null) {
                // Read-only provider points at the existing model and never downloads or deletes, so
                // a test run can't wipe it; the service uses this for the whole run.
                val modelsDir = File(context.filesDir, "models")
                val provider = ReadOnlyModelPathProvider(modelsDir)
                val modelDir = modelsDir.resolve(MODEL_NAME)

                if (!provider.isModelDownloaded(MODEL_NAME)) {
                    println("[cactus-cancel] model missing at ${modelDir.absolutePath} — downloading $MODEL_NAME (one-time, hundreds of MB)…")
                    // Only the *production* provider downloads; its delete-then-download is harmless
                    // when there's no valid model to lose.
                    runBlocking { withTimeout(20.minutes) { CactusModelProvider().getSTTModelPath() } }
                }
                modelPresent = provider.isModelDownloaded(MODEL_NAME)
                println("[cactus-cancel] model dir=${modelDir.absolutePath} present=$modelPresent")

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
                    val load = TimeSource.Monotonic.markNow()
                    runBlocking {
                        svc.earlyInit()
                        withTimeout(2.minutes) { while (!svc.isModelReady) delay(200) }
                    }
                    println("[cactus-cancel] model loaded in ${load.elapsedNow()}")
                    sharedService = svc
                }
            }
        }
        Assume.assumeTrue("STT model '$MODEL_NAME' unavailable (download failed?)", modelPresent)
        service = sharedService!!
    }

    /** ~quiet sine tone PCM_16BIT mono — keeps the model busy for the buffer's full duration. */
    private fun tonePcm(duration: Duration): ByteArray {
        val samples = (SAMPLE_RATE * duration.inWholeMilliseconds / 1000).toInt()
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val v = (sin(2.0 * Math.PI * 220.0 * i / SAMPLE_RATE) * 4000).toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Run a transcription, swallowing the *result* outcome (NoSpeechDetected etc. — we feed a tone,
     * so a blank result is expected). Cancellation is rethrown so it stays cooperative. We only care
     * about timing/completion here, not the recognised text.
     */
    private suspend fun runTranscriptionIgnoringResult(audio: ByteArray) {
        try {
            service.transcribeLocal(audio = audio, sampleRate = SAMPLE_RATE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // result error (e.g. NoSpeechDetected) — irrelevant to a timing test
        }
    }

    /**
     * Baseline: how long does an *uncancelled* local transcription of the buffer take? This is the
     * number a cancel has to beat, and mirrors the runaway inference being investigated.
     */
    @Test
    fun baseline_uncancelledLocalTranscriptionDuration() = runBlocking(Dispatchers.Default) {
        val audio = tonePcm(AUDIO_DURATION)
        val mark = TimeSource.Monotonic.markNow()
        runTranscriptionIgnoringResult(audio)
        val elapsed = mark.elapsedNow()
        println("[cactus-cancel] baseline uncancelled inference of $AUDIO_DURATION audio took $elapsed")
        assertTrue(elapsed > Duration.ZERO)
    }

    /**
     * Cancelling the collecting coroutine mid-inference must make the native call unwind promptly
     * (via cactusStop). We cancel after [PRE_CANCEL_DELAY] and assert the job actually finishes
     * within [MAX_UNWIND]. If native ignores the stop, join() blocks for the whole buffer and the
     * outer withTimeout fails the test with a clear message.
     */
    @Test
    fun cancellingTranscriptionUnwindsPromptly() = runBlocking(Dispatchers.Default) {
        val audio = tonePcm(AUDIO_DURATION)

        val started = CompletableDeferred<Unit>()
        val job: Job = launch {
            started.complete(Unit)
            runTranscriptionIgnoringResult(audio)
        }
        started.await()
        delay(PRE_CANCEL_DELAY)
        assertTrue(
            job.isActive,
            "inference finished before we could cancel (${PRE_CANCEL_DELAY}); increase AUDIO_DURATION",
        )

        val cancelMark = TimeSource.Monotonic.markNow()
        job.cancel()
        try {
            withTimeout(MAX_UNWIND) { job.join() }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError(
                "Native transcription did not unwind within $MAX_UNWIND of cancellation — " +
                    "cactusStop() is not being honoured by the native decode loop.",
            )
        }
        val unwind = cancelMark.elapsedNow()
        println("[cactus-cancel] native inference unwound $unwind after cancel")
        assertTrue(unwind < MAX_UNWIND, "cancellation unwind took $unwind, expected < $MAX_UNWIND")
    }

    /**
     * The phone-side timeout (e.g. the 14s budget) must actually bound the native work. With a
     * [BUDGET] far shorter than the baseline, the call must return at ~[BUDGET], not run to
     * completion. Asserted on elapsed wall time, not on which exception surfaces: a blocking native
     * call that ignores cancellation masks the TimeoutCancellationException, so the type is
     * unreliable — the timing is what matters.
     */
    @Test
    fun withTimeoutBoundsLocalTranscription() = runBlocking(Dispatchers.Default) {
        val audio = tonePcm(AUDIO_DURATION)
        val mark = TimeSource.Monotonic.markNow()
        try {
            withTimeout(BUDGET) { runTranscriptionIgnoringResult(audio) }
        } catch (_: TimeoutCancellationException) {
            // expected when the budget is actually enforced
        }
        val elapsed = mark.elapsedNow()
        println("[cactus-cancel] withTimeout($BUDGET) returned after $elapsed")
        assertTrue(
            elapsed < BUDGET + MAX_UNWIND,
            "withTimeout took $elapsed; native work was not bounded by the $BUDGET budget — " +
                "cactusStop() is not honoured during in-flight inference.",
        )
    }

    /**
     * Read-only view of the already-downloaded model directory. Unlike the production provider it
     * never downloads or deletes, so running the tests can't wipe the on-device model.
     */
    private class ReadOnlyModelPathProvider(private val modelsDir: File) : CactusModelPathProvider {
        override suspend fun getSTTModelPath(): String = modelsDir.resolve(MODEL_NAME).absolutePath
        override suspend fun getLMModelPath(): String = error("LM model not used in this test")
        override fun isModelDownloaded(modelName: String): Boolean =
            modelsDir.resolve(modelName).resolve("config.txt").exists()
        override fun getDownloadedModels(): List<String> =
            modelsDir.listFiles()?.filter { it.resolve("config.txt").exists() }?.map { it.name } ?: emptyList()
        override fun getIncompatibleModels(): List<String> = emptyList()
        override fun deleteModel(modelName: String) { /* never delete in tests */ }
        override fun getModelSizeBytes(modelName: String): Long = 0L
        override fun initTelemetry() {}
    }

    private object NoopAnalytics : CoreAnalytics {
        override fun logEvent(name: String, parameters: Map<String, Any>?) {}
        override suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: kotlin.time.Instant) {}
        override suspend fun processHeartbeat() {}
        override fun updateLastConnectedSerial(serial: String?) {}
        override fun updateRingTransferDurationMetric(duration: Duration) {}
        override fun updateRingLifetimeCollectionCount(serial: String, count: Int) {}
    }
}
