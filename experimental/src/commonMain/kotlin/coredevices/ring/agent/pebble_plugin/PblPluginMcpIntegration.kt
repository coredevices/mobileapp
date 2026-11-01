package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.connection.Plugins
import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpIntegration
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class PblPluginMcpIntegration(
    private val plugin: PluginMetadata,
    private val plugins: Plugins,
): McpIntegration {

    companion object {
        private val logger = Logger.withTag("PblPluginMcpIntegration")
    }

    val pluginUuid get() = plugin.pluginUuid
    override val description: String? get() = plugin.description.takeIf { it.isNotBlank() }

    inner class PebblePluginActionMcpTool(private val declaration: ActionDeclaration): McpTool {
        override val definition: Tool = declaration.toToolDefinition()
        override val extraContext: String? = null

        override suspend fun call(
            jsonInput: String,
            context: SessionContext
        ): ToolCallResult {
            return callTool(
                toolName = declaration.name,
                json = JsonSnake.decodeFromString(jsonInput),
                context = context
            )
        }

    }

    override val name: String
        get() = plugin.name

    override suspend fun connect() {

    }

    override suspend fun close() {

    }

    override suspend fun listTools(): List<McpTool> {
        return plugin.agentActions().map { PebblePluginActionMcpTool(it) }
    }

    override suspend fun callTool(
        toolName: String,
        json: Map<String, JsonElement>,
        context: SessionContext
    ): ToolCallResult {
        val jsonParams = JsonObject(json)
        logger.d { "Invoking action ${pluginUuid}/$toolName with ${json.keys.size} params: ${json.keys.take(10)}" }
        val declaration = plugin.action(toolName)
        if (declaration == null) {
            logger.w { "Action $toolName not found in plugin ${plugin.pluginUuid}" }
            return ToolCallResult(
                resultString = "Action $toolName not found in plugin ${plugin.pluginUuid}",
                semanticResult = SemanticResult.GenericFailure(
                    userErrorMessage = "Action $toolName not found",
                    llmRecoverable = true
                )
            )
        }
        if (declaration.requiresConfirmation && !context.userConfirmed) {
            logger.d { "Action $toolName requires confirmation" }
            return ToolCallResult(
                resultString = "Waiting for the user to confirm this action",
                semanticResult = SemanticResult.RequiresConfirmation(
                    integrationName = name,
                    toolName = toolName,
                    arguments = jsonParams,
                    destructive = declaration.destructive,
                    description = declaration.description.takeIf { it.isNotBlank() },
                )
            )
        }

        val result = plugins.invoke(PluginCaller.Host(), plugin, toolName, jsonParams)
        val errorLine = listOfNotNull(result.code, result.message).joinToString(": ").ifEmpty { null }
        if (!result.ok) {
            logger.w { "Error during invocation of $toolName: $errorLine" }
        }
        return ToolCallResult(
            resultString = errorLine ?: result.text ?: "OK",
            semanticResult = when {
                result.ok -> SemanticResult.GenericSuccess
                else -> SemanticResult.GenericFailure(
                    userErrorMessage = result.message,
                    llmRecoverable = true
                )
            }
        )
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? = null
}

internal fun ActionDeclaration.toToolDefinition(): Tool {
    val schema = this.parameters ?: JsonObject(mapOf())
    return Tool(
        name = name,
        inputSchema = McpJson.decodeFromJsonElement(schema),
        description = description,
    )
}