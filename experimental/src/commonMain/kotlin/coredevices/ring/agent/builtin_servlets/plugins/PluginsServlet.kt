package coredevices.ring.agent.builtin_servlets.plugins

import io.rebble.libpebblecommon.connection.Plugins
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import io.rebble.libpebblecommon.plugin.PluginMetadata

/** Reads plugin data sources; [sourcePlugins] is re-evaluated on every call. */
class PluginsServlet(
    private val sourcePlugins: suspend () -> List<PluginMetadata>,
    plugins: Plugins,
): BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        ReadSourceTool(sourcePlugins, plugins)
    )
) {
    override suspend fun getExtraContext(sessionContext: SessionContext?): String? =
        listOfNotNull(super.getExtraContext(sessionContext), sourcePlugins().describeSources())
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }

    companion object {
        const val NAME = "builtin_plugins"
        const val SOURCES_HEADER = "Data sources readable with '${ReadSourceTool.TOOL_NAME}', listed as " +
            "'<plugin> → category \"<category>\", item \"<item>\"'. Call it with exactly that category and " +
            "item; the plugin name is never passed. Pass an instance id only to narrow a source with " +
            "multiple instances. Read a source before answering from it."
    }
}

internal fun List<PluginMetadata>.describeSources(): String? {
    val lines = flatMap { plugin ->
        plugin.sources.flatMap { source ->
            source.items.map { item ->
                val multiple = if (source.supportsMultiple) " [multiple instances]" else ""
                "- ${plugin.name} → category \"${source.category}\", item \"$item\"$multiple"
            }
        }
    }
    if (lines.isEmpty()) return null
    return (listOf(PluginsServlet.SOURCES_HEADER) + lines).joinToString("\n")
}
