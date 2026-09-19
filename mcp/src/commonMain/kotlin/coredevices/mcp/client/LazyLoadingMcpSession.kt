package coredevices.mcp.client

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * An [McpSession] that keeps the tool list offered to the model small. Once the session holds more
 * than [maxEagerTools] tools, only the [eagerIntegrations] are listed directly; every other
 * integration becomes a group the model can pull in through a synthetic `load_tools` tool, whose
 * description catalogues the groups with a few sample tools each. Loaded groups stay loaded for
 * the life of the session.
 *
 * Callers keep using [listTools] and [callTool] as with any session, so the agent harness only
 * has to re-list tools between inference rounds to pick up what was loaded. The loader is only
 * rebuilt when the deferrable tools change, so the tool specs the model sees stay stable between
 * rounds.
 */
class LazyLoadingMcpSession(
    integrations: List<McpIntegration>,
    scope: CoroutineScope,
    private val eagerIntegrations: Set<String>,
    private val maxEagerTools: Int = DEFAULT_MAX_EAGER_TOOLS,
) : McpSession(integrations, scope) {
    private val loadedGroups = mutableSetOf<String>()
    private var loader: LoadToolsTool? = null

    override suspend fun listTools(): List<McpSessionTool> {
        val allTools = super.listTools()
        if (allTools.size <= maxEagerTools) {
            loader = null
            return allTools
        }
        val (eager, deferrable) = allTools.partition { it.integrationName in eagerIntegrations }
        val loadTools = loader?.takeIf { it.catalog.describes(deferrable) }
            ?: LoadToolsTool(ToolCatalog(deferrable), ::load)
        loader = loadTools
        val loaded = deferrable.filter { it.integrationName in loadedGroups }
        return eager + loaded + McpSessionTool(LOADER_INTEGRATION_NAME, loadTools)
    }

    override suspend fun callTool(
        integrationName: String,
        toolName: String,
        jsonInput: Map<String, JsonElement>,
        context: SessionContext,
        requireExists: Boolean
    ): ToolCallResult {
        if (integrationName != LOADER_INTEGRATION_NAME) {
            return super.callTool(integrationName, toolName, jsonInput, context, requireExists)
        }
        val loader = loader ?: return ToolCallResult(
            "There are no tool groups to load; all tools are already available.",
            SemanticResult.GenericFailure("Invalid tool call", llmRecoverable = true)
        )
        return loader.call(McpJson.encodeToString(jsonInput), context)
    }

    override suspend fun getExtraContext(context: SessionContext?, includePromptsFrom: Map<String, Set<String>>): String? {
        val base = super.getExtraContext(context, includePromptsFrom)
        if (loader == null) return base
        return listOfNotNull(base, LOADER_EXTRA_CONTEXT).joinToString("\n")
    }

    private fun load(group: String?): ToolCallResult {
        val groups = loader?.catalog?.groups.orEmpty()
        val tools = if (group != null) groups[group] else null
        if (group == null || tools == null) {
            val available = groups.keys.joinToString(", ")
            return ToolCallResult(
                "Unknown tool group '${group.orEmpty()}'. Available groups: $available",
                SemanticResult.GenericFailure("Unknown tool group", llmRecoverable = true)
            )
        }
        loadedGroups += group
        val names = tools.joinToString(", ") { it.tool.definition.name }
        return ToolCallResult(
            "Loaded ${tools.size} tools from '$group', now available to call: $names",
            SemanticResult.SupportingData(
                "Loaded MCP $group with ${tools.size} tools",
                assistiveOnly = true
            )
        )
    }

    companion object {
        const val DEFAULT_MAX_EAGER_TOOLS = 20
        const val LOADER_INTEGRATION_NAME = "tool_loader"
        const val LOAD_TOOLS_NAME = "load_tools"
        private const val LOADER_EXTRA_CONTEXT =
            "Some tools are grouped by MCP server and are not callable until loaded with '$LOAD_TOOLS_NAME'. " +
                "Load the group whose sample tools best match the request before attempting to use it."
    }
}

/**
 * Tools grouped by integration, with a short sample of each group for the loader's description.
 * Tools named `namespace__tool` are sampled round-robin across namespaces so the sample hints at
 * the group's full breadth.
 */
internal class ToolCatalog(
    tools: List<McpSessionTool>,
    private val samplesPerGroup: Int = SAMPLES_PER_GROUP,
    private val sampleDescriptionLength: Int = SAMPLE_DESCRIPTION_LENGTH,
) {
    val groups: Map<String, List<McpSessionTool>> = tools.groupBy { it.integrationName }
    private val definitions = tools.definitions()

    fun describes(tools: List<McpSessionTool>): Boolean = definitions == tools.definitions()

    private fun List<McpSessionTool>.definitions() = map { it.integrationName to it.tool.definition }

    fun describe(): String = groups.entries.joinToString("\n") { (group, tools) ->
        val samples = sample(tools).joinToString(", ") { describeSample(it.tool.definition) }
        val remaining = tools.size - minOf(tools.size, samplesPerGroup)
        val more = if (remaining > 0) " and $remaining more" else ""
        "- $group (${tools.size} tools): $samples$more"
    }

    private fun sample(tools: List<McpSessionTool>): List<McpSessionTool> {
        val byNamespace = tools.groupBy { it.tool.definition.name.substringBefore(NAMESPACE_SEPARATOR, "") }.values
        val sampled = mutableListOf<McpSessionTool>()
        var index = 0
        while (sampled.size < samplesPerGroup) {
            val round = byNamespace.mapNotNull { it.getOrNull(index) }
            if (round.isEmpty()) break
            sampled += round.take(samplesPerGroup - sampled.size)
            index++
        }
        return sampled
    }

    private fun describeSample(tool: Tool): String {
        val description = tool.description?.trim().orEmpty()
        if (description.isEmpty()) return tool.name
        val short = if (description.length > sampleDescriptionLength) {
            description.take(sampleDescriptionLength - 1).trimEnd() + "…"
        } else description
        return "${tool.name} ($short)"
    }

    companion object {
        const val SAMPLES_PER_GROUP = 3
        const val SAMPLE_DESCRIPTION_LENGTH = 50
        private const val NAMESPACE_SEPARATOR = "__"
    }
}

internal class LoadToolsTool(
    val catalog: ToolCatalog,
    private val load: (group: String?) -> ToolCallResult,
) : BuiltInMcpTool(
    definition = Tool(
        name = LazyLoadingMcpSession.LOAD_TOOLS_NAME,
        description = "Loads every tool of one MCP server group so it can be called. " +
            "Available groups, each with a sample of its tools:\n" + catalog.describe(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put(GROUP_PARAM, buildJsonObject {
                    put("type", "string")
                    put("description", "Name of the group to load")
                    putJsonArray("enum") { catalog.groups.keys.forEach { add(JsonPrimitive(it)) } }
                })
            },
            required = listOf(GROUP_PARAM)
        )
    )
) {
    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val args = McpJson.decodeFromString<Map<String, JsonElement>>(jsonInput)
        return load(args[GROUP_PARAM]?.jsonPrimitive?.contentOrNull)
    }

    companion object {
        const val GROUP_PARAM = "group"
    }
}
