package coredevices.ring.agent.pebble_plugin

import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * A built-in servlet that is also a [NativePlugin]: each tool is an action with the tool's input
 * schema as its parameters. [name] stays the servlet id the agent and settings persist, so the
 * plugin's display name is [pluginName], carried by [metadata].
 */
abstract class BuiltInServletPlugin(
    name: String,
    tools: List<McpTool>,
    override val pluginUuid: Uuid,
    val pluginName: String,
    override val description: String,
    private val callerPermission: String,
) : BuiltInMcpIntegration(name, tools), NativePlugin {
    private val toolsByName: Map<String, McpTool> by lazy { tools.associateBy { it.definition.name } }

    protected open val clock: Clock get() = Clock.System

    override val sources: List<SourceDeclaration> get() = emptyList()

    override val actions: List<ActionDeclaration> by lazy { tools.map(::declareAction) }

    override val metadata: PluginMetadata
        get() = PluginMetadata(pluginUuid, pluginName, description, sources, actions)

    protected open fun declareAction(tool: McpTool): ActionDeclaration = ActionDeclaration(
        name = tool.definition.name,
        description = tool.definition.description.orEmpty(),
        parameters = McpJson.encodeToJsonElement(tool.definition.inputSchema).jsonObject,
        callerPermissions = listOf(PluginPermission(callerPermission)),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        limit: Int?,
    ): Flow<SourceEnvelope> = emptyFlow()

    override suspend fun invoke(action: String, args: JsonObject): ActionResult {
        val tool = toolsByName[action]
            ?: return ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "$pluginName has no action $action")
        // Servlets switch tools off by state, e.g. messaging with no approved contacts.
        if (listTools().none { it.definition.name == action }) {
            return ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "$action is not available right now")
        }
        return toActionResult(tool.call(McpJson.encodeToString(args), pluginSessionContext()))
    }

    /** A plugin caller has no recording behind it: the call is "now", with no transcript. */
    protected open fun pluginSessionContext(): SessionContext =
        // Named, or this picks the `parent: Job?` overload and the deferred never completes.
        SessionContext(timeBase = clock.now(), userMessageText = CompletableDeferred(value = null))

    protected open fun toActionResult(result: ToolCallResult): ActionResult = when (val semantic = result.semanticResult) {
        is SemanticResult.GenericFailure -> ActionResult.error(
            code = if (semantic.llmRecoverable) PluginErrors.INVALID_ARGS else PluginErrors.UNKNOWN,
            message = semantic.userErrorMessage ?: result.resultString,
        )
        is SemanticResult.RequiresConfirmation -> ActionResult.error(
            PluginErrors.PERMISSION_DENIED,
            "${semantic.toolName} needs the user's confirmation, which only the Index agent can ask for",
        )
        SemanticResult.UserDenied -> ActionResult.error(PluginErrors.PERMISSION_DENIED, "The user declined")
        else -> ActionResult(ok = true, text = semantic.toastText() ?: result.resultString)
    }

    /** Short enough for a watch toast, where the tool's raw result is JSON for a model. */
    protected open fun SemanticResult?.toastText(): String? = when (this) {
        is SemanticResult.AlarmCreation -> "Alarm set"
        is SemanticResult.TimerCreation -> "Timer set"
        is SemanticResult.TaskCreation -> "Reminder created"
        is SemanticResult.ListItemCreation -> listUsed?.let { "Added to $it" } ?: "Saved"
        is SemanticResult.MessageSent -> "Sent to $recipientName"
        is SemanticResult.ActionLogged -> title
        else -> null
    }
}
