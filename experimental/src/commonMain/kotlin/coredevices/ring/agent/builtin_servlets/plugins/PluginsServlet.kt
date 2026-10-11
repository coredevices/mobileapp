package coredevices.ring.agent.builtin_servlets.plugins

import coredevices.analytics.CoreAnalytics
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import io.rebble.libpebblecommon.plugin.Plugin

/** Reads plugin data sources; [sourcePlugins] is re-evaluated on every call. */
class PluginsServlet(
    private val sourcePlugins: suspend () -> List<Plugin>,
    analytics: CoreAnalytics,
): BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        ReadSourceTool(sourcePlugins, analytics)
    )
) {
    override suspend fun getExtraContext(sessionContext: SessionContext?): String? =
        listOfNotNull(super.getExtraContext(sessionContext), sourcePlugins().describeSources())
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }

    companion object {
        const val NAME = "builtin_plugins"
        const val SOURCES_HEADER = "Data sources readable with '${ReadSourceTool.TOOL_NAME}', as '<plugin>: " +
            "<category>/<item>'. Call it with the category and item; pass an instance id " +
            "only to narrow a source with multiple instances. Read a source before answering from it."
    }
}

internal fun List<Plugin>.describeSources(): String? {
    val lines = flatMap { plugin ->
        plugin.sources.flatMap { source ->
            source.items.map { item ->
                val multiple = if (source.supportsMultiple) " [multiple instances]" else ""
                "- ${plugin.name}: ${source.category}/$item$multiple"
            }
        }
    }
    if (lines.isEmpty()) return null
    return (listOf(PluginsServlet.SOURCES_HEADER) + lines).joinToString("\n")
}
