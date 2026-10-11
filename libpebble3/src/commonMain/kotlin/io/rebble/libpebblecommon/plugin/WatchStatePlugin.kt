package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.Watches
import io.rebble.libpebblecommon.locker.Locker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

class WatchStatePlugin(
    private val locker: Locker,
    private val watches: Watches,
) : NativePlugin {
    override val pluginUuid: Uuid = BUILT_IN_WATCH_STATE_UUID
    override val name: String = "Watch State"
    override val description: String = "Launch apps and watchfaces on the watch."

    override val sources: List<SourceDeclaration> = emptyList()

    override val actions: List<ActionDeclaration> = listOf(
        ActionDeclaration(
            name = ACTION_LAUNCH_APP,
            description = "Open an app or watchface on the watch, by its uuid. It has to be in the user's locker.",
            parameters = schema(""""$PARAM_UUID":{"type":"string"}""", required = listOf(PARAM_UUID)),
            callerPermissions = LAUNCH,
        ),
        ActionDeclaration(
            name = ACTION_LAUNCH_PREVIOUS_APP,
            description = "Go back to the app or watchface that was running before the current one.",
            parameters = schema(""),
            callerPermissions = LAUNCH,
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> = emptyFlow()

    override suspend fun invoke(action: String, args: JsonObject): ActionResult {
        val connected = watches.watches.value.filterIsInstance<ConnectedPebbleDevice>()
        return when (action) {
            ACTION_LAUNCH_APP -> {
                val uuid = args[PARAM_UUID]?.jsonPrimitive?.content?.let(Uuid::parseOrNull)
                    ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "'$PARAM_UUID' must be an app uuid")
                val app = locker.getLockerApp(uuid).first()
                    ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "no app $uuid in the locker")
                if (connected.isEmpty()) return noWatch()
                launch(uuid, connected)
                ActionResult(ok = true, text = "Opened ${app.properties.title}.")
            }
            ACTION_LAUNCH_PREVIOUS_APP -> {
                if (connected.isEmpty()) return noWatch()
                // Each watch goes back to its own previous app.
                val launched = connected.mapNotNull { watch ->
                    watch.previousApp.value?.also { launch(it, listOf(watch)) }
                }
                if (launched.isEmpty()) {
                    return ActionResult.error(PluginErrors.INVALID_REQUEST, "no previous app since the watch connected")
                }
                val title = locker.getLockerApp(launched.first()).first()?.properties?.title ?: "the previous app"
                ActionResult(ok = true, text = "Opened $title.")
            }
            else -> ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "no action $action")
        }
    }

    // As LibPebble.launchApp, which can't be injected here: it depends on the plugin registry.
    private suspend fun launch(uuid: Uuid, watches: List<ConnectedPebbleDevice>) {
        locker.maybeSetActiveWatchface(uuid, onlyIfNotAlreadySet = false)
        watches.forEach { it.launchApp(uuid) }
    }

    private fun noWatch() = ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "no watch connected")

    private fun schema(properties: String, required: List<String> = emptyList()): JsonObject {
        val requiredJson = required.joinToString(",") { "\"$it\"" }
        return Json.decodeFromString(
            JsonObject.serializer(),
            """{"type":"object","properties":{$properties},"required":[$requiredJson]}""",
        )
    }

    companion object {
        const val ACTION_LAUNCH_APP = "launch_app"
        const val ACTION_LAUNCH_PREVIOUS_APP = "launch_previous_app"
        const val PARAM_UUID = "uuid"

        private val LAUNCH = listOf(PluginPermission(PluginPermissions.LAUNCH_APPS))

        val BUILT_IN_WATCH_STATE_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000007")
    }
}
