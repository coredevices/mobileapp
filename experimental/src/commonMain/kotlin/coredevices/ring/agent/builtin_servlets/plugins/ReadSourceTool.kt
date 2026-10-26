package coredevices.ring.agent.builtin_servlets.plugins

import io.rebble.libpebblecommon.connection.Plugins
import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ReadSourceTool(
    private val sourcePlugins: suspend () -> List<PluginMetadata>,
    private val plugins: Plugins,
): BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(mapOf(
                "category" to JsonObject(mapOf(
                    "type" to JsonPrimitive("string"),
                    "description" to JsonPrimitive("Namespace of the data source, e.g. " +
                            "'weather', 'home'"
                    )
                )),
                "item" to JsonObject(mapOf(
                    "type" to JsonPrimitive("string"),
                    "description" to JsonPrimitive("The kind of item or 'path' to read data " +
                            "for, e.g. 'location', 'light'"
                    )
                )),
                "instance" to JsonObject(mapOf(
                    "type" to JsonPrimitive("string"),
                    "description" to JsonPrimitive("The specific instance identifier of the " +
                            "item to read, if known & supported, e.g. a uuid of a specific location, " +
                            "'outdoor_floodlight_1', etc."
                    )
                ))
            )),
            required = listOf("category", "item")
        )
    )
) {

    @Serializable
    data class ReadSourceArgs(
        val category: String,
        val item: String,
        val instance: String? = null
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
                    instances = args.instance?.let { instanceId ->
                        envelope.instances.filter { it.instanceId == instanceId }
                    } ?: envelope.instances
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
                " Returns data for all instances if unspecified"
        private val logger = Logger.withTag("ReadSourceTool")
    }
}