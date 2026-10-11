package coredevices.ring.agent.builtin_servlets.plugins

import coredevices.analytics.HEARTBEAT_COUNTER_PLUGIN_OBSERVATIONS
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.RecordingCoreAnalytics
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.Plugin
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val PLUGIN_UUID = Uuid.parse("00000000-0000-0000-0002-00000000000a")

private class FakePlugin(
    override val sources: List<SourceDeclaration>,
    private val envelopes: Flow<SourceEnvelope>,
) : Plugin {
    override val pluginUuid = PLUGIN_UUID
    override val name = "Fake"
    override val description = ""
    val observed = mutableListOf<Pair<String, String>>()

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        observed += category to item
        return envelopes
    }
}

private fun weatherSource() = SourceDeclaration(
    category = "weather",
    items = listOf("location"),
    properties = mapOf("temperature" to listOf(SourceShapeNames.SHORT_TEXT)),
)

private fun instance(id: String, temperature: String) = SourceInstance(
    instanceId = id,
    properties = mapOf("temperature" to mapOf(SourceShapeNames.SHORT_TEXT to JsonPrimitive(temperature))),
)

private val context = SessionContext(timeBase = null, userMessageText = CompletableDeferred(null))

class ReadSourceToolTest {

    private val home = instance("home", "21°C")
    private val work = instance("work", "18°C")

    private val analytics = RecordingCoreAnalytics()

    private fun tool(plugin: Plugin? = null) = ReadSourceTool({ listOfNotNull(plugin) }, analytics)

    private fun servingPlugin(vararg instances: SourceInstance) = FakePlugin(
        sources = listOf(weatherSource()),
        envelopes = flowOf(SourceEnvelope(PLUGIN_UUID.toString(), null, instances.toList())),
    )

    private fun decodeInstances(resultString: String): List<SourceInstance> {
        val json = JsonSnake.parseToJsonElement(resultString).jsonObject
        assertEquals(PLUGIN_UUID.toString(), json.getValue("plugin_uuid").jsonPrimitive.content)
        return JsonSnake.decodeFromJsonElement(
            ListSerializer(SourceInstance.serializer()),
            json.getValue("instances"),
        )
    }

    @Test
    fun returnsEveryInstanceWhenNoneIsRequested() = runTest {
        val plugin = servingPlugin(home, work)

        val result = tool(plugin).call("""{"category":"weather","item":"location"}""", context)

        assertEquals(listOf("weather" to "location"), plugin.observed)
        assertEquals(listOf(home, work), decodeInstances(result.resultString))
        val semantic = assertIs<SemanticResult.SupportingData>(result.semanticResult)
        assertEquals("Read weather/location", semantic.summary)
    }

    @Test
    fun filtersToTheRequestedInstance() = runTest {
        val result = tool(servingPlugin(home, work)).call(
            """{"category":"weather","item":"location","instance":"work"}""",
            context,
        )

        assertEquals(listOf(work), decodeInstances(result.resultString))
        assertEquals(
            "Read weather/location/work",
            assertIs<SemanticResult.SupportingData>(result.semanticResult).summary,
        )
    }

    @Test
    fun unknownInstanceYieldsAnEmptyList() = runTest {
        val result = tool(servingPlugin(home)).call(
            """{"category":"weather","item":"location","instance":"nowhere"}""",
            context,
        )

        assertEquals(emptyList(), decodeInstances(result.resultString))
    }

    @Test
    fun failsRecoverablyWhenNoPluginServesThePath() = runTest {
        val result = tool(servingPlugin(home)).call(
            """{"category":"home","item":"light","instance":"porch"}""",
            context,
        )

        assertContains(result.resultString, "home/light/porch")
        val failure = assertIs<SemanticResult.GenericFailure>(result.semanticResult)
        assertTrue(failure.llmRecoverable)
    }

    @Test
    fun failsRecoverablyWhenThePluginNeverEmits() = runTest {
        val silent = FakePlugin(sources = listOf(weatherSource()), envelopes = MutableSharedFlow())

        val result = tool(silent).call("""{"category":"weather","item":"location"}""", context)

        assertContains(result.resultString, "Timed out")
        val failure = assertIs<SemanticResult.GenericFailure>(result.semanticResult)
        assertTrue(failure.llmRecoverable)
    }

    @Test
    fun observationsAreCountedForTheHeartbeat() = runTest {
        val tool = tool(servingPlugin(home))

        tool.call("""{"category":"weather","item":"location"}""", context)
        tool.call("""{"category":"weather","item":"location"}""", context)
        tool.call("""{"category":"home","item":"light"}""", context)

        assertEquals(mapOf(HEARTBEAT_COUNTER_PLUGIN_OBSERVATIONS to 2), analytics.counters)
    }
}
