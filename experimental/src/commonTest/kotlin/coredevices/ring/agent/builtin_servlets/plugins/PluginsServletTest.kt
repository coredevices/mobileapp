package coredevices.ring.agent.builtin_servlets.plugins

import coredevices.ring.agent.RecordingCoreAnalytics
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.Plugin
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
) : Plugin {
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
    )

    private val phone = DeclaringPlugin(
        name = "Phone",
        sources = listOf(SourceDeclaration(category = "phone", items = listOf("state", "network"))),
    )

    @Test
    fun describesEachItemOfEachPlugin() {
        val summary = listOf(weather, phone).describeSources()

        assertEquals(
            listOf(
                PluginsServlet.SOURCES_HEADER,
                "- Weather: weather/location [multiple instances]",
                "- Phone: phone/state",
                "- Phone: phone/network",
            ).joinToString("\n"),
            summary,
        )
    }

    @Test
    fun noSourcesMeansNoSummary() {
        assertNull(emptyList<Plugin>().describeSources())
        assertNull(listOf(DeclaringPlugin("Empty", emptyList())).describeSources())
    }

    @Test
    fun servletExtraContextCarriesTheSummary() = runTest {
        val context = PluginsServlet({ listOf(phone) }, RecordingCoreAnalytics()).getExtraContext(null)

        assertContains(context.orEmpty(), PluginsServlet.SOURCES_HEADER)
        assertContains(context.orEmpty(), "- Phone: phone/state")
    }

    @Test
    fun servletExtraContextIsNullWithoutSources() = runTest {
        assertNull(PluginsServlet({ emptyList() }, RecordingCoreAnalytics()).getExtraContext(null))
    }
}
