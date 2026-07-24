package coredevices.coreapp.agent

import androidx.test.platform.app.InstrumentationRegistry
import com.cactus.cactusComplete
import com.cactus.cactusDestroy
import com.cactus.cactusInit
import com.cactus.cactusSetBackend
import com.cactus.isCactusSupported
import coredevices.coreapp.testsupport.NeedleTestTools
import coredevices.coreapp.testsupport.NoopAnalytics
import coredevices.coreapp.testsupport.ReadOnlyModelPathProvider
import coredevices.ring.model.CactusModelProvider
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.STTConfig
import coredevices.util.models.CactusSTTMode
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.NoOpInferenceBoost
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Validation: replay the ACTUAL bug-report recordings through the real on-device pipeline —
 * [CactusTranscriptionService] (parakeet STT) then needle (cactus) tool routing — and confirm they
 * reproduce the host-side analysis and the fixed behaviour. This is the on-device counterpart to
 * pebble-model-eval/run_issue_recordings.py: same audio, but the app's real pre/post processing.
 *
 *   MOB-9829 rec 50 "Milk, eggs, butter."          -> 3 well-formed create_list_item
 *   MOB-9703 rec 15 "...on july twentieth."         -> create_reminder with an ordinal date
 *   MOB-9812 rec 54 "Turn on the laundry lights."   -> transcribes (a healthy clip from that issue)
 *   MOB-9812 rec 60 (field: blank / wedge)          -> logged for host==device comparison (not asserted)
 */
class IssueRecordingPipelineTest {
    private companion object {
        const val STT_MODEL = "parakeet-tdt-0.6b-v3"
        const val SAMPLE_RATE = 16_000

        private var stt: CactusTranscriptionService? = null
        private var needle: Long = 0L
        private var ready = false

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            if (needle != 0L) cactusDestroy(needle)
        }
    }

    @Before
    fun setUp() {
        Assume.assumeTrue("Cactus unsupported on this CPU", isCactusSupported())
        if (stt == null) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val modelsDir = File(context.filesDir, "models")
            val provider = ReadOnlyModelPathProvider(modelsDir, STT_MODEL)
            Assume.assumeTrue("parakeet not downloaded", provider.isModelDownloaded(STT_MODEL))
            val svc = CactusTranscriptionService(
                coreConfigFlow = CoreConfigFlow(MutableStateFlow(CoreConfig(sttConfig = STTConfig(mode = CactusSTTMode.LocalOnly, modelName = STT_MODEL)))),
                modelProvider = provider,
                analytics = NoopAnalytics,
                inferenceBoost = NoOpInferenceBoost(),
            )
            val needlePath = runBlocking {
                svc.earlyInit()
                withTimeout(2.minutes) { while (!svc.isModelReady) delay(200) }
                CactusModelProvider().getLMModelPath() // needle-pebble-ft (already downloaded)
            }
            cactusSetBackend("cpu")
            needle = cactusInit(needlePath, null, false)
            stt = svc
            ready = needle != 0L
        }
        Assume.assumeTrue("needle model unavailable", ready)
    }

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    private fun transcribe(pcm: ByteArray): String =
        runBlocking { stt!!.transcribeLocal(audio = pcm, sampleRate = SAMPLE_RATE, timeout = 30.seconds) }

    private fun toolCalls(text: String): List<Pair<String, JsonObject>> {
        if (text.isBlank()) return emptyList()
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", "user"); put("content", text) })
        }.toString()
        return NeedleTestTools.parseCalls(cactusComplete(needle, messages, NeedleTestTools.OPTIONS_JSON, NeedleTestTools.TOOLS_JSON, null))
    }

    private fun run(asset: String): Pair<String, List<Pair<String, JsonObject>>> {
        val tx = transcribe(asset(asset))
        val calls = toolCalls(tx)
        println("[pipeline] $asset -> STT='${tx.take(80)}' calls=${calls.map { it.first }}")
        return tx to calls
    }

    @Test
    fun mob9829_rec50_multiItem_addsThreeItems() {
        val (tx, calls) = run("eval_issue_9829.raw")
        assertTrue(tx.isNotBlank(), "MOB-9829 rec 50 STT blank")
        assertTrue(NeedleTestTools.wellFormedListItems(calls) == 3, "MOB-9829 rec 50: expected 3 list items, got ${calls.map { it.first }}")
    }

    @Test
    fun mob9703_rec15_ordinalDate_createsReminder() {
        val (tx, calls) = run("eval_issue_9703.raw")
        assertTrue(tx.lowercase().contains("twent"), "MOB-9703 rec 15 STT missing ordinal: '$tx'")
        assertTrue(calls.any { it.first == "create_reminder" }, "MOB-9703 rec 15: expected create_reminder, got ${calls.map { it.first }}")
    }

    @Test
    fun mob9812_rec54_healthyClip_transcribes() {
        val (tx, _) = run("eval_issue_9812_rec54.raw")
        assertTrue(tx.isNotBlank() && tx.lowercase().contains("laundry"), "MOB-9812 rec 54 STT unexpected: '$tx'")
    }

    /** rec 60 failed in the field (the wedge). On a healthy handle we just record what STT returns. */
    @Test
    fun mob9812_rec60_borderline_logged() {
        run("eval_issue_9812_rec60.raw")
    }
}
