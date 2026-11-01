package coredevices.ring.agent.builtin_servlets.plugins

import coredevices.ring.agent.pebble_plugin.FakePlugins
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private class DeclaringPlugin(
    override val name: String,
    override val sources: List<SourceDeclaration>,
) : NativePlugin {
    override val pluginUuid = Uuid.random()
    override val description = ""
    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> = emptyFlow()
}

class PluginsServletTest {

    private val weather = DeclaringPlugin(
        name = "Weather",
        sources = listOf(
            SourceDeclaration(
                category = "weather",
                items = listOf("location"),
                supportsMultiple = true,
            )
        ),
    ).metadata

    private val phone = DeclaringPlugin(
        name = "Phone",
        sources = listOf(SourceDeclaration(category = "phone", items = listOf("state", "network"))),
    ).metadata

    @Test
    fun describesEachItemOfEachPlugin() {
        val summary = listOf(weather, phone).describeSources()

        assertEquals(
            listOf(
                PluginsServlet.SOURCES_HEADER,
                "- Weather → category \"weather\", item \"location\" [multiple instances]",
                "- Phone → category \"phone\", item \"state\"",
                "- Phone → category \"phone\", item \"network\"",
            ).joinToString("\n"),
            summary,
        )
    }

    @Test
    fun noSourcesMeansNoSummary() {
        assertNull(emptyList<PluginMetadata>().describeSources())
        assertNull(listOf(DeclaringPlugin("Empty", emptyList()).metadata).describeSources())
    }

    @Test
    fun servletExtraContextCarriesTheSummary() = runTest {
        val context = PluginsServlet({ listOf(phone) }, FakePlugins()).getExtraContext(null)

        assertContains(context.orEmpty(), PluginsServlet.SOURCES_HEADER)
        assertContains(context.orEmpty(), "- Phone → category \"phone\", item \"state\"")
    }

    @Test
    fun servletExtraContextIsNullWithoutSources() = runTest {
        assertNull(PluginsServlet({ emptyList() }, FakePlugins()).getExtraContext(null))
    }
}
