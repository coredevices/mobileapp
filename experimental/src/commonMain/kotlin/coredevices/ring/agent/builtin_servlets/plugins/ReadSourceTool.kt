package coredevices.ring.agent.builtin_servlets.plugins

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceUpdate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

class ReadSourceTool(
    private val sourcePlugins: suspend () -> List<PluginMetadata>,
    private val plugins: Plugins,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
): BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(TOOL_NAME)
) {

    @ToolDefinition(servlet = PluginsServlet.NAME, name = TOOL_NAME, description = TOOL_DESCRIPTION)
    @Serializable
    data class ReadSourceArgs(
        @ToolParam("Namespace of the data source, e.g. " + "'weather', 'home'")
        val category: String,
        @ToolParam("The kind of item or 'path' to read data " + "for, e.g. 'location', 'light'")
        val item: String,
        @ToolParam("The specific instance identifier of the " + "item to read, if known & supported, e.g. a uuid of a specific location, " + "'outdoor_floodlight_1', etc.")
        val instance: String? = null,
    )

    @Serializable
    data class ReadSourceResult(
        val pluginUuid: String,
        val instances: List<SourceInstance>,
    )

    override suspend fun call(
        jsonInput: String,
        context: SessionContext
    ): ToolCallResult {
        val args = JsonSnake.decodeFromString<ReadSourceArgs>(jsonInput)
        logger.d { "Reading source ${args.category}/${args.item}" }
        val plugin = sourcePlugins().firstOrNull { it.serves(args.category, args.item) }
            ?: run {
                logger.w {
                    "Failed to read ${args.category}/${args.item}: couldn't find plugin"
                }
                return ToolCallResult(
                    resultString = "No source found for ${args.category}/${args.item}${args.instance?.let { "/$it" } ?: ""}",
                    semanticResult = SemanticResult.GenericFailure(
                        userErrorMessage = "Couldn't find plugin",
                        llmRecoverable = true
                    )
                )
            }

        val envelope = when (val update = plugins.read(PluginCaller.Host(), plugin, args.category, args.item)) {
            is SourceUpdate.Data -> update.envelope
            is SourceUpdate.Error -> {
                logger.e { "Failed to read ${args.category}/${args.item}: ${update.code} ${update.message}" }
                return ToolCallResult(
                    resultString = "Failed to read ${args.category}/${args.item}: ${update.message}",
                    semanticResult = SemanticResult.GenericFailure(
                        userErrorMessage = update.message,
                        llmRecoverable = true
                    )
                )
            }
        }

        return ToolCallResult(
            resultString = JsonSnake.encodeToString(
                ReadSourceResult(
                    pluginUuid = envelope.pluginUuid,
                    instances = (args.instance?.let { instanceId ->
                        envelope.instances.filter { it.instanceId == instanceId }
                    } ?: envelope.instances).map { it.renderTimesForModel(zone) }
                )
            ),
            semanticResult = SemanticResult.SupportingData(
                "Read ${args.category}/${args.item}${args.instance?.let { "/$it" } ?: ""} from ${plugin.name}"
            )
        )
    }

    companion object {
        const val TOOL_NAME = "read_source"
        const val TOOL_DESCRIPTION = "Read data from a data source path: <category>/<item>/[instance]." +
                " Returns data for all instances if unspecified. Time series and timestamps are given" +
                " in local wall-clock time; quote those times rather than computing your own"
        private val logger = Logger.withTag("ReadSourceTool")
    }
}