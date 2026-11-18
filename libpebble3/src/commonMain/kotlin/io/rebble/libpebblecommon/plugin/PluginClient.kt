package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.LibPebbleAnalytics
import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.permissions.PermissionSubject
import io.rebble.libpebblecommon.permissions.RunningApps
import io.rebble.libpebblecommon.permissions.invokeActionKey
import io.rebble.libpebblecommon.permissions.refusalMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.uuid.Uuid

sealed interface PluginCaller {
    /** Checking plugin state for the UI rather than acting for the user: never prompts, isn't counted. */
    val probe: Boolean get() = false

    /** A PKJS app, held to each plugin's caller permissions. */
    data class App(val subject: PermissionSubject, val name: String) : PluginCaller

    /** The phone app itself (e.g. Index), trusted with every plugin. */
    data class Host(override val probe: Boolean = false) : PluginCaller
}

object PluginHeartbeatCounters {
    const val HOST_INVOCATIONS = "plugin.invocation_count"
    const val HOST_OBSERVATIONS = "plugin.observation_count"
    const val PKJS_INVOCATIONS = "plugin.pkjs_invocation_count"
    const val PKJS_OBSERVATIONS = "plugin.pkjs_observation_count"

    val all = listOf(HOST_INVOCATIONS, HOST_OBSERVATIONS, PKJS_INVOCATIONS, PKJS_OBSERVATIONS)

    const val SUBSCRIPTIONS = "subscription_count"
    const val INVOCATIONS = "invocation_count"

    fun perPlugin(pluginUuid: Uuid, metric: String) = "plugin.$pluginUuid.$metric"
}

class PluginClient(
    private val registry: PluginRegistry,
    private val permissions: AppPermissions,
    private val runningApps: RunningApps,
    private val analytics: LibPebbleAnalytics,
    private val pluginLogs: PluginLogs,
    private val actionLog: PluginActionRecorder,
) : Plugins {
    private val logger = Logger.withTag("PluginClient")

    override val disabledBuiltInPlugins: StateFlow<Set<Uuid>> get() = registry.disabledBuiltIns

    override fun setBuiltInPluginEnabled(pluginUuid: Uuid, enabled: Boolean) =
        registry.setBuiltInEnabled(pluginUuid, enabled)

    override fun registerPlugin(plugin: NativePlugin) = registry.registerPlugin(plugin)

    override fun unregisterPlugin(pluginUuid: Uuid) {
        registry.unregisterPlugin(pluginUuid)
    }

    override val preferredSourcePlugins: StateFlow<Map<String, Uuid>> get() = registry.preferredSources

    override fun setPreferredSourcePlugin(category: String, item: String, pluginUuid: Uuid?) =
        registry.setPreferredSource(category, item, pluginUuid)

    override fun sourcePlugin(category: String, item: String): PluginMetadata? =
        registry.findSourcePlugin(category, item)?.metadata

    override fun nativePlugins(): Flow<List<PluginMetadata>> = registry.nativePlugins()

    override fun configMessageTarget(pluginUuid: String): ConfigMessageTarget? = registry.configMessageTarget(pluginUuid)

    override fun getAllPlugins(): List<PluginMetadata> = registry.getAllPlugins()

    override suspend fun read(
        caller: PluginCaller,
        plugin: PluginMetadata,
        category: String,
        item: String,
        properties: List<String>?,
        timeout: Duration,
    ): SourceUpdate = withTimeoutOrNull(timeout) {
        observe(caller, plugin, category, item, properties).firstOrNull()
            ?: SourceUpdate.Error(PluginErrors.UNKNOWN, "$category/$item ended without data")
    } ?: SourceUpdate.Error(PluginErrors.TIMEOUT, "no data from $category/$item in $timeout")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(
        caller: PluginCaller,
        plugin: PluginMetadata,
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        limit: Int?,
    ): Flow<SourceUpdate> = flow {
        val target = registry.findPlugin(plugin.pluginUuid.toString())
        val declaration = target?.sources?.firstOrNull { it.serves(category, item) }
        if (target == null || declaration == null) {
            emit(SourceUpdate.Error(PluginErrors.PLUGIN_UNAVAILABLE, "${plugin.name} has no source $category/$item"))
            return@flow
        }
        // Whatever the caller chooses reaches the plugin, which may send it off the phone.
        val declared = properties?.filter { it in declaration.properties }
        val pixels = iconPixelSize?.clamped()
        val cap = limit.clampedLimit()
        count(caller, target.pluginUuid, PluginHeartbeatCounters.SUBSCRIPTIONS, PluginHeartbeatCounters.HOST_OBSERVATIONS, PluginHeartbeatCounters.PKJS_OBSERVATIONS)
        if (caller !is PluginCaller.App) {
            emitAll(serve(caller, target, declaration, category, item, declared, pixels, cap))
            return@flow
        }
        val required = requiredOfCaller(declaration)
        // Asked for what the app declared: `Notifications/<package>` rather than all of them.
        val asked = required.flatMap { permissions.callerKeys(caller.subject.appUuid, it) }.distinct()
        permissions.requestAll(caller.subject, asked, wait = true)
        // Re-evaluated as grants change, so allowing or revoking takes effect at once.
        emitAll(
            permissions.grantedKeys(caller.subject, asked)
                // A source without the property a partial grant is scoped by can't be narrowed to it.
                .map { granted -> callerScope(required, granted)?.takeIf { declaration.properties.keys.containsAll(it.keys) } }
                .distinctUntilChanged()
                .flatMapLatest { scope ->
                    if (scope != null) {
                        // The scoping properties are needed to filter by, whether asked for or not.
                        val wanted = declared?.plus(scope.keys)?.distinct()
                        serve(caller, target, declaration, category, item, wanted, pixels, cap).map { it.scopedTo(scope) }
                    } else {
                        val refused = asked.associateWith { permissions.check(caller.subject, it) }
                            .filterValues { it != PermissionDecision.Granted }
                        val message = if (refused.isEmpty()) {
                            "${caller.name} holds ${asked.joinToString()}, but $category/$item needs ${required.joinToString()}"
                        } else {
                            refusalMessage(caller.name, refused)
                        }
                        flowOf(SourceUpdate.Error(PluginErrors.PERMISSION_DENIED, message))
                    }
                }
        )
    }.map { it.takeInstances(limit.clampedLimit() ?: Plugin.MAX_LIMIT) }

    private fun Int?.clampedLimit() = this?.coerceIn(1, Plugin.MAX_LIMIT)

    private fun SourceUpdate.takeInstances(limit: Int): SourceUpdate =
        if (this !is SourceUpdate.Data) this
        else SourceUpdate.Data(envelope.copy(instances = envelope.instances.take(limit)))

    override suspend fun invoke(
        caller: PluginCaller,
        plugin: PluginMetadata,
        action: String,
        args: JsonObject,
        timeout: Duration?,
    ): ActionResult = run(caller, plugin, action, args, timeout).also { result ->
        // Args can carry user data, so only their names.
        logger.i {
            "${caller.label} invoked ${plugin.pluginUuid}/$action with ${args.keys}: " +
                if (result.ok) "ok" else "${result.code} ${result.message}"
        }
    }

    private suspend fun run(
        caller: PluginCaller,
        plugin: PluginMetadata,
        action: String,
        args: JsonObject,
        timeout: Duration?,
    ): ActionResult {
        val target = registry.findPlugin(plugin.pluginUuid.toString())
        val declaration = target?.action(action)
            ?: return ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "${plugin.name} has no action $action")
        if (caller is PluginCaller.App) {
            val required = requiredOfCaller(declaration).toMutableSet()
            // The args can carry the caller's data, which the plugin could send off the phone.
            if (plugin.pluginUuid != caller.subject.appUuid && permissions.canExfiltrate(plugin.pluginUuid)) {
                required += invokeActionKey(plugin.pluginUuid)
            }
            val refused = permissions.requestAll(caller.subject, required, wait = true)
            if (refused.isNotEmpty()) {
                return ActionResult.error(PluginErrors.PERMISSION_DENIED, refusalMessage(caller.name, refused))
            }
        }
        val missing = declaration.requiredParams.filterNot { args.containsKey(it) }
        if (missing.isNotEmpty()) {
            return ActionResult.error(PluginErrors.INVALID_ARGS, "missing required: ${missing.joinToString()}")
        }
        count(caller, target.pluginUuid, PluginHeartbeatCounters.INVOCATIONS, PluginHeartbeatCounters.HOST_INVOCATIONS, PluginHeartbeatCounters.PKJS_INVOCATIONS)
        val run = suspend {
            withTimeoutOrNull(timeout ?: Duration.INFINITE) {
                try {
                    target.invoke(action, args, prompt = !caller.probe)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "action $action threw" }
                    ActionResult.error(PluginErrors.UNKNOWN, e.message ?: "error")
                }
            } ?: ActionResult.error(PluginErrors.TIMEOUT, "no response in $timeout")
        }
        // A probe can't ask for anything, so it doesn't bring the plugin's requests to the user.
        val result = if (caller.probe) run() else runningApps.whileRunning(plugin.pluginUuid) { run() }
        if (caller is PluginCaller.App) actionLog.record(caller.subject.appUuid, target.pluginUuid, action, args, result)
        return result
    }

    private fun serve(
        caller: PluginCaller,
        plugin: Plugin,
        declaration: SourceDeclaration,
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        limit: Int?,
    ): Flow<SourceUpdate> = plugin.observe(category, item, properties, iconPixelSize, limit, prompt = !caller.probe)
        .map { update ->
            if (update !is SourceUpdate.Data) return@map update
            SourceUpdate.Data(
                update.envelope.validated(declaration, iconPixelSize) { property, problem ->
                    val message = "$category/$item.$property: $problem"
                    // Its developer can act on it; nobody outside this app can fix a built-in.
                    if (plugin !is NativePlugin) pluginLogs.log(plugin, message)
                    reportOnce("${plugin.name} ($message)")
                }
            )
        }
        .catch { t ->
            logger.w(t) { "${caller.label} reading ${plugin.pluginUuid}/$category/$item failed" }
            emit(SourceUpdate.Error(PluginErrors.UNKNOWN, t.message ?: t::class.simpleName ?: "error"))
        }
        .onStart { if (!caller.probe) runningApps.started(plugin.pluginUuid) }
        .onCompletion { if (!caller.probe) runningApps.stopped(plugin.pluginUuid) }

    // A plugin polled every minute would otherwise say the same thing every minute.
    private val reported = MutableStateFlow(emptySet<String>())

    private fun reportOnce(message: String) {
        if (message in reported.getAndUpdate { it + message }) return
        logger.w { message }
    }

    private fun count(caller: PluginCaller, pluginUuid: Uuid, perPlugin: String, host: String, pkjs: String) {
        if (caller.probe) return
        analytics.incrementHeartbeatCounter(if (caller is PluginCaller.App) pkjs else host)
        analytics.incrementHeartbeatCounter(PluginHeartbeatCounters.perPlugin(pluginUuid, perPlugin))
    }

    private val PluginCaller.label: String
        get() = when (this) {
            is PluginCaller.App -> "$name (${subject.appUuid})"
            is PluginCaller.Host -> if (probe) "host probe" else "host"
        }
}
