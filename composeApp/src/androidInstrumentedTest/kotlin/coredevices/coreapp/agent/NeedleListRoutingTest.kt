package coredevices.coreapp.agent

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.cactus.cactusComplete
import com.cactus.cactusDestroy
import com.cactus.cactusInit
import com.cactus.cactusSetBackend
import com.cactus.isCactusSupported
import coredevices.coreapp.testsupport.NeedleTestTools
import coredevices.ring.model.CactusModelProvider
import coredevices.util.CommonBuildKonfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * On-device regression for MOB-9829: a spoken multi-item list ("Milk, eggs, butter.") must route to
 * one `create_list_item` per item, not a single item or a malformed call. This drives the real
 * on-device needle LM through the same `cactusComplete` path [coredevices.ring.agent.IndexAgentCactus]
 * uses, so it guards the fine-tuned needle shipped at v2.0.1 (the fix lives in the model weights, so
 * text -> tool calls is the correct layer — STT is not involved).
 *
 * Runs the actual native model. If the needle LM isn't present it's downloaded on demand (one-time,
 * from the `CACTUS_WEIGHTS_VERSION` release). Skips on CPUs where Cactus is unsupported (e.g. some
 * emulators). Run against a persistent install so the model isn't wiped between runs:
 *
 *   adb shell am instrument -w \
 *     -e class coredevices.coreapp.agent.NeedleListRoutingTest \
 *     coredevices.coreapp.test/androidx.test.runner.AndroidJUnitRunner
 */
class NeedleListRoutingTest {
    private companion object {
        val MODEL_NAME = CommonBuildKonfig.CACTUS_LM_MODEL_NAME // needle-pebble-ft
    }

    private lateinit var modelPath: String

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue("Cactus unsupported on this CPU (e.g. x86 emulator)", isCactusSupported())

        // Reuses the app's model provider (same as CactusLocalCancellationTest): downloads the
        // needle LM on first run, then serves the cached copy.
        val provider = CactusModelProvider()
        if (!provider.isModelDownloaded(MODEL_NAME)) {
            println("[needle] '$MODEL_NAME' not present — downloading (one-time)…")
        }
        modelPath = runBlocking { withTimeout(20.minutes) { provider.getLMModelPath() } }
        println("[needle] model at $modelPath")
    }

    @Test
    fun multiItemList_emitsOneWellFormedListItemPerItem() {
        cactusSetBackend("cpu")
        val handle = cactusInit(modelPath, null, false)
        assertTrue(handle != 0L, "cactusInit failed for $modelPath")
        try {
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "Milk, eggs, butter.")
                })
            }.toString()

            val result = cactusComplete(handle, messages, NeedleTestTools.OPTIONS_JSON, NeedleTestTools.TOOLS_JSON, null)
            val calls = NeedleTestTools.parseCalls(result)
            val wellFormed = NeedleTestTools.wellFormedListItems(calls)

            println("[needle] tool calls=${calls.map { it.first }} wellFormedListItems=$wellFormed")
            assertEquals(
                3,
                wellFormed,
                "MOB-9829: 'Milk, eggs, butter.' should route to 3 well-formed create_list_item calls",
            )
        } finally {
            cactusDestroy(handle)
        }
    }
}
