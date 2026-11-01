package coredevices.mcp.client

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * An [McpSession] that keeps the tool list offered to the model small. Once the session holds at
 * least [lazyLoadThreshold] tools, only the [eagerIntegrations] are listed directly; every other
 * integration becomes a group the model can pull in through a synthetic `load_tools` tool, whose
 * description catalogues the groups with a few sample tools each. A companion `search_tools` finds
 * the group providing a capability by keyword. Loaded groups stay loaded for the life of the session.
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
    private val lazyLoadThreshold: Int = DEFAULT_LAZY_LOAD_THRESHOLD,
) : McpSession(integrations, scope) {
    private val loadedGroups = mutableSetOf<String>()
    private var loader: Loader? = null

    private inner class Loader(val catalog: ToolCatalog) {
        val loadTool = McpSessionTool(LOADER_INTEGRATION_NAME, LoadToolsTool(catalog, ::load))
        val searchTool = McpSessionTool(LOADER_INTEGRATION_NAME, SearchToolsTool(::search))
        val tools get() = listOf(loadTool, searchTool)
    }

    override suspend fun listTools(): List<McpSessionTool> {
        val allTools = super.listTools()
        if (allTools.size < lazyLoadThreshold) {
            loader = null
            return allTools
        }
        val (eager, deferrable) = allTools.partition { it.integrationName in eagerIntegrations }
        val loader = loader?.takeIf { it.catalog.describes(deferrable) } ?: Loader(ToolCatalog(deferrable))
        this.loader = loader
        val loaded = deferrable.filter { it.integrationName in loadedGroups }
        return eager + loaded + loader.tools
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
        val tool = when (toolName) {
            LOAD_TOOLS_NAME -> loader.loadTool
            SEARCH_TOOLS_NAME -> loader.searchTool
            else -> return ToolCallResult(
                "Unknown tool '$toolName'. Available: $LOAD_TOOLS_NAME, $SEARCH_TOOLS_NAME",
                SemanticResult.GenericFailure("Invalid tool call", llmRecoverable = true)
            )
        }
        return tool.tool.call(McpJson.encodeToString(jsonInput), context)
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
        // The model must address loaded tools by their full declared names, not the bare ones.
        val names = tools.joinToString(", ") { it.compositeName() }
        return ToolCallResult(
            "Loaded ${tools.size} tools from '$group', now available to call by these exact names: $names",
            SemanticResult.SupportingData("Loaded MCP $group with ${tools.size} tools")
        )
    }

    private fun search(keywords: List<String>): ToolCallResult {
        val catalog = loader?.catalog ?: return ToolCallResult(
            "There are no tool groups to search; all tools are already available.",
            SemanticResult.GenericFailure("Invalid tool call", llmRecoverable = true)
        )
        if (keywords.isEmpty()) return ToolCallResult(
            "No keywords given. Pass one or more keywords describing the capability needed.",
            SemanticResult.GenericFailure("Invalid tool call", llmRecoverable = true)
        )
        val summary = SemanticResult.SupportingData("Searched tools for ${keywords.joinToString(", ")}")
        val matches = catalog.search(keywords)
        if (matches.isEmpty()) {
            val available = catalog.groups.keys.joinToString(", ")
            return ToolCallResult("No tools match ${keywords.joinToString(", ")}. Available groups: $available", summary)
        }
        val byGroup = matches.groupBy { it.group }.entries.joinToString("\n") { (group, hits) ->
            val state = if (group in loadedGroups) "already loaded" else "load with $LOAD_TOOLS_NAME group='$group'"
            "- $group ($state): " + hits.joinToString(", ") { catalog.describeTool(it.tool.tool.definition) }
        }
        return ToolCallResult("Matching tools by group:\n$byGroup", summary)
    }

    companion object {
        const val DEFAULT_LAZY_LOAD_THRESHOLD = 20
        const val LOADER_INTEGRATION_NAME = "tool_loader"
        const val LOAD_TOOLS_NAME = "load_tools"
        const val SEARCH_TOOLS_NAME = "search_tools"
        private const val LOADER_EXTRA_CONTEXT =
            "Some tools are grouped by MCP server and are not callable until loaded with '$LOAD_TOOLS_NAME'. " +
                "Load the group whose description or sample tools best match the request before attempting to use it. " +
                "If no group or already available tool fits, call '$SEARCH_TOOLS_NAME' with keywords to find the group to load."
    }
}

/**
 * Tools grouped by integration, with a short sample of each group for the loader's description.
 * A group whose integration describes itself leads with that and samples tool names only; otherwise
 * each sample carries its own description. Tools named `namespace__tool` are sampled round-robin
 * across namespaces so the sample hints at the group's full breadth.
 */
internal class ToolCatalog(
    tools: List<McpSessionTool>,
    private val samplesPerGroup: Int = SAMPLES_PER_GROUP,
    private val sampleDescriptionLength: Int = SAMPLE_DESCRIPTION_LENGTH,
) {
    val groups: Map<String, List<McpSessionTool>> = tools.groupBy { it.integrationName }
    private val definitions = tools.definitions()

    fun describes(tools: List<McpSessionTool>): Boolean = definitions == tools.definitions()

    private fun List<McpSessionTool>.definitions() =
        map { Triple(it.integrationName, it.integrationDescription, it.tool.definition) }

    fun describe(): String = groups.entries.joinToString("\n") { (group, tools) ->
        "- $group (${tools.size} tools): ${describeGroup(tools)}"
    }

    fun search(keywords: List<String>, limit: Int = SEARCH_RESULT_LIMIT): List<ToolMatch> =
        groups.flatMap { (group, tools) ->
            tools.map { tool ->
                val definition = tool.tool.definition
                val score = ToolSearch.score(keywords, definition.name, definition.description, group, tool.integrationDescription)
                ToolMatch(group, tool, score)
            }
        }.filter { it.score > 0 }.sortedByDescending { it.score }.take(limit)

    fun describeTool(tool: Tool): String {
        val description = tool.description?.trim().orEmpty()
        if (description.isEmpty()) return tool.name
        return "${tool.name} (${shorten(description, sampleDescriptionLength)})"
    }

    private fun describeGroup(tools: List<McpSessionTool>): String {
        val description = groupDescription(tools)
        val samples = sample(tools).joinToString(", ") {
            if (description != null) it.tool.definition.name else describeTool(it.tool.definition)
        }
        val remaining = tools.size - minOf(tools.size, samplesPerGroup)
        val more = if (remaining > 0) " and $remaining more" else ""
        val intro = description?.let { "${it.trimEnd('.')}. Tools: " }.orEmpty()
        return intro + samples + more
    }

    private fun groupDescription(tools: List<McpSessionTool>): String? =
        tools.firstNotNullOfOrNull { it.integrationDescription?.trim()?.takeIf(String::isNotEmpty) }
            ?.let { shorten(it, GROUP_DESCRIPTION_LENGTH) }

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

    private fun shorten(text: String, length: Int): String =
        if (text.length > length) text.take(length - 1).trimEnd() + "…" else text

    companion object {
        const val SAMPLES_PER_GROUP = 3
        const val SAMPLE_DESCRIPTION_LENGTH = 50
        const val GROUP_DESCRIPTION_LENGTH = 200
        const val SEARCH_RESULT_LIMIT = 10
        private const val NAMESPACE_SEPARATOR = "__"
    }
}

internal data class ToolMatch(val group: String, val tool: McpSessionTool, val score: Int)

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

internal class SearchToolsTool(
    private val search: (keywords: List<String>) -> ToolCallResult,
) : BuiltInMcpTool(
    definition = Tool(
        name = LazyLoadingMcpSession.SEARCH_TOOLS_NAME,
        description = "Finds tools in every MCP server group whose name or description matches any of the given keywords, " +
            "and names the group to pass to '${LazyLoadingMcpSession.LOAD_TOOLS_NAME}'. " +
            "Use it when no group in that tool's catalogue obviously fits the request.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put(KEYWORDS_PARAM, buildJsonObject {
                    put("type", "array")
                    put("description", "Keywords describing the capability needed, such as the nouns and verbs of the request")
                    put("items", buildJsonObject { put("type", "string") })
                })
            },
            required = listOf(KEYWORDS_PARAM)
        )
    )
) {
    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val args = McpJson.decodeFromString<Map<String, JsonElement>>(jsonInput)
        val keywords = when (val raw = args[KEYWORDS_PARAM]) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> raw.contentOrNull?.split(',').orEmpty()
            else -> emptyList()
        }
        return search(keywords.map(String::trim).filter { it.isNotEmpty() })
    }

    companion object {
        const val KEYWORDS_PARAM = "keywords"
    }
}
