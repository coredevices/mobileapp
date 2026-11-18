package coredevices.ring.agent.builtin_servlets.bugreport

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Files a bug report the same way the bug report screen does. Implemented by the app module. */
fun interface BugReporter {
    suspend fun report(description: String, watch: Boolean): Result<Unit>
}

class CreateBugReportTool(
    private val bugReporter: BugReporter,
) : BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(TOOL_NAME),
    extraContext = "When the user asks to create or file a bug report, call '$TOOL_NAME'. " +
        "'watch' or 'Pebble watch' means device 'watch'; 'Index' or 'ring' means device 'index'. " +
        "If no device is named, use 'index'."
) {
    companion object {
        private val logger = Logger.withTag("CreateBugReportTool")
        const val TOOL_NAME = "create_bug_report"
        const val TOOL_DESCRIPTION = "Create and send a bug report for the user's Pebble watch or Index ring"
    }

    @ToolDefinition(servlet = BugReportServlet.NAME, name = TOOL_NAME, description = TOOL_DESCRIPTION)
    @Serializable
    private data class Args(
        @ToolParam("'watch' for the Pebble watch, 'index' for the Index ring")
        val device: String,
        @ToolParam("Everything the user said after naming the device, verbatim")
        val description: String,
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val args = JsonSnake.decodeFromString<Args>(jsonInput)
        val watch = args.device.equals("watch", ignoreCase = true)
        val result = try {
            bugReporter.report(args.description.trim(), watch)
        } catch (e: Exception) {
            Result.failure(e)
        }
        return result.fold(
            onSuccess = {
                ToolCallResult(
                    "{\"success\": true}",
                    SemanticResult.ActionLogged(
                        toolName = TOOL_NAME,
                        title = if (watch) "Watch bug report sent" else "Index bug report sent",
                        success = true,
                        body = args.description,
                    )
                )
            },
            onFailure = {
                logger.e(it) { "Failed to create bug report" }
                val message = it.message ?: "Failed to send bug report"
                ToolCallResult(
                    buildJsonObject {
                        put("success", false)
                        put("error_message", message)
                    }.toString(),
                    SemanticResult.GenericFailure(message)
                )
            }
        )
    }
}
