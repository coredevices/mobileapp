package coredevices.coreapp.transcription

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.analytics.CoreAnalytics
import coredevices.coreapp.testsupport.ReadOnlyModelPathProvider
import coredevices.ring.model.CactusModelProvider
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.STTConfig
import coredevices.util.models.CactusSTTMode
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.NoOpInferenceBoost
import coredevices.util.transcription.TRANSCRIPTION_FAILURE_EVENT
import coredevices.util.transcription.TRANSCRIPTION_SUCCESS_EVENT
import coredevices.util.transcription.TranscriptionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

private class RecordingAnalytics : CoreAnalytics {
    val events = CopyOnWriteArrayList<Pair<String, Map<String, Any>?>>()
    fun count(name: String) = events.count { (event, params) -> event == name && params?.get("service") == "cactus" }
    override fun logEvent(name: String, parameters: Map<String, Any>?) { events.add(name to parameters) }
    override suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant) {}
    override suspend fun processHeartbeat() {}
    override fun updateLastConnectedSerial(serial: String?) {}
    override fun updateRingTransferDurationMetric(duration: Duration) {}
    override fun updateRingLifetimeCollectionCount(serial: String, count: Int) {}
    override fun updateRingBatteryVoltage(voltageMilliV: Int) {}
}

class CactusLocalCancellationTest {
    private companion object {
        const val TAG = "CactusCancelTest"
        const val MODEL_NAME = "parakeet-tdt-0.6b-v3"
        const val SAMPLE_RATE = 16_000

        val CLIPS = listOf(
            "eval_issue_9812_rec60.raw",
            "eval_set_timer_15min.raw",
            "eval_issue_9829.raw",
            "eval_issue_9812_rec54.raw",
            "eval_note_jared_size10.raw",
            "eval_shopping_list_shrimp.raw",
            "eval_set_alarm_750am.raw",
            "eval_note_danny_lacurious.raw",
            "eval_issue_9703.raw",
            "eval_reminder_30min.raw",
            "eval_reminder_11am_tomorrow.raw",
            "eval_text_eric_shrimp.raw",
            "eval_note_long_half_sizes.raw",
        )

        const val HEALTH_CLIP = "eval_shopping_list_shrimp.raw"
        const val HEALTH_KEYWORD = "cornstarch"

        const val KNOWN_BLANK_CLIP = "eval_issue_9812_rec60.raw"

        val RETURN_BUDGET = 1500.milliseconds
        val STOP_BUDGET = 3.seconds
        val DRAIN_BUDGET = 40.seconds

        private val initLock = Any()
        private val analytics = RecordingAnalytics()
        private var sharedService: CactusTranscriptionService? = null
        private var modelPresent = false
        private val clipCache = mutableMapOf<String, ByteArray>()
    }

    private lateinit var service: CactusTranscriptionService

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        synchronized(initLock) {
            if (sharedService == null) {
                val modelsDir = File(context.filesDir, "models")
                val provider = ReadOnlyModelPathProvider(modelsDir, MODEL_NAME)
                if (!provider.isModelDownloaded(MODEL_NAME)) {
                    Log.i(TAG, "[cancel] model missing — downloading $MODEL_NAME (one-time)…")
                    runBlocking { withTimeout(20.minutes) { CactusModelProvider().getSTTModelPath(MODEL_NAME) } }
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
                        analytics = analytics,
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
        Assume.assumeTrue("STT model '$MODEL_NAME' unavailable", modelPresent)
        service = sharedService!!
    }

    private fun clip(name: String): ByteArray = clipCache.getOrPut(name) {
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }
    }

    private fun ByteArray.durationSeconds() = size / (SAMPLE_RATE * 2.0)

    private fun busy(): Boolean {
        val field = CactusTranscriptionService::class.java.getDeclaredField("transcriptionMutex")
        field.isAccessible = true
        return (field.get(service) as Mutex).isLocked
    }

    private suspend fun awaitDrain(limit: Duration = DRAIN_BUDGET): Duration {
        val mark = TimeSource.Monotonic.markNow()
        withTimeout(limit) { while (busy()) delay(25) }
        return mark.elapsedNow()
    }

    private suspend fun transcribe(audio: ByteArray, timeout: Duration? = null): String =
        service.transcribeLocal(audio = audio, sampleRate = SAMPLE_RATE, timeout = timeout)

    private fun assertHealthy(text: String, where: String) {
        assertTrue(
            text.lowercase().contains(HEALTH_KEYWORD),
            "[$where] handle produced '$text', expected it to contain '$HEALTH_KEYWORD' — " +
                "an aborted run left the model unusable.",
        )
    }

    @Test
    fun baseline_everyClipTranscribes() = runBlocking(Dispatchers.Default) {
        for (name in CLIPS) {
            val audio = clip(name)
            val mark = TimeSource.Monotonic.markNow()
            val text = transcribe(audio)
            val elapsed = mark.elapsedNow()
            Log.i(TAG, "[cancel] baseline %-34s %5.2fs audio -> %s (%d chars)".format(name, audio.durationSeconds(), elapsed, text.length))
            if (name != KNOWN_BLANK_CLIP) {
                assertTrue(text.isNotBlank(), "[$name] transcribed blank")
            }
            awaitDrain()
        }
    }

    @Test
    fun timeoutBoundsTheCaller_everyClip() = runBlocking(Dispatchers.Default) {
        val budget = 1.seconds
        for (name in CLIPS) {
            val audio = clip(name)
            val mark = TimeSource.Monotonic.markNow()
            try {
                transcribe(audio, timeout = budget)
            } catch (_: Exception) {
            }
            val elapsed = mark.elapsedNow()
            val drain = awaitDrain()
            Log.i(TAG, "[cancel] timeout   %-34s returned in %s, drained in %s".format(name, elapsed, drain))
            assertTrue(
                elapsed < budget + RETURN_BUDGET,
                "[$name] caller returned after $elapsed, expected < ${budget + RETURN_BUDGET} — " +
                    "the native call is not detached from the caller.",
            )
            assertTrue(
                drain < STOP_BUDGET,
                "[$name] native call drained in $drain, expected < $STOP_BUDGET — cactusStop() did not abort it.",
            )
        }
    }

    @Test
    fun cancellationUnwindsTheCaller_everyClip() = runBlocking(Dispatchers.Default) {
        for (name in CLIPS) {
            val audio = clip(name)
            val started = CompletableDeferred<Unit>()
            val job: Job = launch {
                started.complete(Unit)
                try {
                    transcribe(audio)
                } catch (_: CancellationException) {
                    throw CancellationException("cancelled")
                } catch (_: Exception) {
                }
            }
            started.await()
            delay(300)
            val mark = TimeSource.Monotonic.markNow()
            job.cancel()
            withTimeout(RETURN_BUDGET) { job.join() }
            val unwind = mark.elapsedNow()
            val drain = awaitDrain()
            Log.i(TAG, "[cancel] cancel    %-34s unwound in %s, drained in %s".format(name, unwind, drain))
            assertTrue(unwind < RETURN_BUDGET, "[$name] unwind took $unwind")
            assertTrue(
                drain < STOP_BUDGET,
                "[$name] native call drained in $drain, expected < $STOP_BUDGET — cactusStop() did not abort it.",
            )
        }
    }

    @Test
    fun handleStaysHealthyAfterAbort_everyClip() = runBlocking(Dispatchers.Default) {
        val health = clip(HEALTH_CLIP)
        for (name in CLIPS) {
            val audio = clip(name)
            try {
                transcribe(audio, timeout = 700.milliseconds)
            } catch (_: Exception) {
            }
            awaitDrain()
            val text = transcribe(health)
            awaitDrain()
            Log.i(TAG, "[cancel] health after abort of %-34s -> '%s'".format(name, text.trim()))
            assertHealthy(text, "after abort of $name")
        }
    }

    @Test
    fun concurrentRequestIsRefusedThenRecovers() = runBlocking(Dispatchers.Default) {
        val long = clip("eval_text_eric_shrimp.raw")
        val health = clip(HEALTH_CLIP)

        val inFlight = async { transcribe(long) }
        withTimeout(RETURN_BUDGET) { while (!busy()) delay(10) }

        val refused = try {
            transcribe(health)
            false
        } catch (_: TranscriptionException.TranscriptionInProgress) {
            true
        }
        assertTrue(refused, "a request during an in-flight native call should be refused")

        inFlight.await()
        val drain = awaitDrain()
        val text = transcribe(health)
        Log.i(TAG, "[cancel] recovered after ${drain} -> '${text.trim()}'")
        assertHealthy(text, "after drain")
    }

    @Test
    fun cancellationBeforeDispatchNeverStrandsTheHandle() = runBlocking(Dispatchers.Default) {
        val audio = clip("eval_text_eric_shrimp.raw")
        repeat(40) { i ->
            try {
                transcribe(audio, timeout = Duration.ZERO)
            } catch (_: Exception) {
            }
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                try { transcribe(audio) } catch (_: Exception) {}
            }
            job.cancel()
            withTimeout(RETURN_BUDGET) { job.join() }
            try {
                awaitDrain()
            } catch (_: Exception) {
                throw AssertionError("[iteration $i] the handle was never released")
            }
        }
        assertHealthy(transcribe(clip(HEALTH_CLIP)), "after pre-dispatch cancellations")
    }

    private fun assertAnalytics(successes: Int, failures: Int, where: String) {
        assertEquals(successes, analytics.count(TRANSCRIPTION_SUCCESS_EVENT), "[$where] cactus success events")
        assertEquals(failures, analytics.count(TRANSCRIPTION_FAILURE_EVENT), "[$where] cactus failure events")
    }

    @Test
    fun analytics_completedRunLogsOneSuccess() = runBlocking(Dispatchers.Default) {
        awaitDrain()
        analytics.events.clear()
        transcribe(clip(HEALTH_CLIP))
        awaitDrain()
        assertAnalytics(successes = 1, failures = 0, where = "completed run")
    }

    @Test
    fun analytics_timedOutRunLogsOneFailureAndNoSuccess() = runBlocking(Dispatchers.Default) {
        awaitDrain()
        analytics.events.clear()
        try {
            transcribe(clip("eval_text_eric_shrimp.raw"), timeout = 500.milliseconds)
        } catch (_: Exception) {
        }
        awaitDrain()
        assertAnalytics(successes = 0, failures = 1, where = "timed-out run")
    }

    @Test
    fun analytics_cancelledRunLogsNothing() = runBlocking(Dispatchers.Default) {
        awaitDrain()
        analytics.events.clear()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            started.complete(Unit)
            try {
                transcribe(clip("eval_text_eric_shrimp.raw"))
            } catch (_: Exception) {
            }
        }
        started.await()
        delay(300)
        job.cancel()
        job.join()
        awaitDrain()
        assertAnalytics(successes = 0, failures = 0, where = "cancelled run")
    }

    @Test
    fun concurrentCallersYieldAtMostOneWinner() = runBlocking(Dispatchers.Default) {
        val audio = clip(HEALTH_CLIP)
        repeat(8) { round ->
            val outcomes = (0 until 12).map {
                async {
                    try {
                        transcribe(audio, timeout = 8.seconds)
                        "ok"
                    } catch (e: Exception) {
                        e::class.simpleName ?: "unknown"
                    }
                }
            }.awaitAll()
            val winners = outcomes.count { it == "ok" }
            assertTrue(winners <= 1, "[round $round] $winners callers ran at once: $outcomes")
            awaitDrain()
        }
        assertHealthy(transcribe(audio), "after concurrent rounds")
    }
}
