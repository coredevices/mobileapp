package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_SHORTCUTS
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.Watches
import io.rebble.libpebblecommon.locker.AppBasicProperties
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.Locker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

class WatchStatePlugin(
    private val locker: Locker,
    private val watches: Watches,
) : NativePlugin {
    override val pluginUuid: Uuid = BUILT_IN_WATCH_STATE_UUID
    override val name: String = "Watch State"
    override val description: String = "List and launch the apps and watchfaces in the locker."

    override val sources: List<SourceDeclaration> = listOf(APPS_SOURCE)

    override val actions: List<ActionDeclaration> = listOf(
        LAUNCH_APP_ACTION,
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
    ): Flow<SourceEnvelope> {
        if (category != CATEGORY || item != ITEM_APP) return emptyFlow()
        return locker.getAllLockerBasicInfo().map(::appsEnvelope)
    }

    override suspend fun invoke(action: String, args: JsonObject): ActionResult {
        val connected = watches.watches.value.filterIsInstance<ConnectedPebbleDevice>()
        return when (action) {
            ACTION_LAUNCH_APP -> {
                val uuid = (args[PARAM_UUID] ?: args[ActionDeclaration.PARAM_INSTANCE_ID])
                    ?.jsonPrimitive?.content?.let(Uuid::parseOrNull)
                    ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "'$PARAM_UUID' must be an app uuid")
                val title = locker.getLockerApp(uuid).first()?.properties?.title
                    ?: SYSTEM_SHORTCUTS[uuid]
                    ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "no app $uuid in the locker or system shortcuts")
                if (connected.isEmpty()) return noWatch()
                launch(uuid, connected)
                ActionResult(ok = true, text = "Opened $title.")
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
                val title = locker.getLockerApp(launched.first()).first()?.properties?.title
                    ?: SYSTEM_SHORTCUTS[launched.first()]
                    ?: "the previous app"
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

    companion object {
        private val LAUNCH = listOf(PluginPermission(PluginPermissions.LAUNCH_APPS))

        const val CATEGORY = "watch"
        const val ITEM_APP = "app"
        /** `watchapp` or `watchface`. */
        const val PROPERTY_TYPE = "type"

        val LAUNCH_APP_ACTION = ActionDeclaration(
            name = ACTION_LAUNCH_APP,
            description = "Open an app or watchface on the watch, by its uuid. It has to be in the user's locker, or be a system shortcut (e.g. Timeline Future, Quiet Time).",
            // Neither is required, so `uuid` callers keep working and a $CATEGORY/$ITEM_APP tile can bind `instanceId`.
            parameters = schema(
                """
                "$PARAM_UUID":{"type":"string"},
                "${ActionDeclaration.PARAM_INSTANCE_ID}":{"type":"string","description":"Id from $CATEGORY/$ITEM_APP, the same uuid."}
                """,
            ),
            targets = listOf("$CATEGORY/$ITEM_APP"),
            callerPermissions = LAUNCH,
        )

        val APPS_SOURCE = SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_APP),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_TYPE to listOf(SourceShapeNames.SHORT_TEXT),
            ),
            supportsMultiple = true,
            callerPermissions = listOf(PluginPermission(PluginPermissions.READ_APPS)),
        )

        const val ACTION_LAUNCH_APP = "launch_app"
        const val ACTION_LAUNCH_PREVIOUS_APP = "launch_previous_app"
        const val PARAM_UUID = "uuid"

        val BUILT_IN_WATCH_STATE_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000007")
    }
}

/** One instance per launchable locker app, keyed by its uuid: plugin-only pbws have nothing to open. */
internal fun appsEnvelope(apps: List<AppBasicProperties>) = SourceEnvelope(
    pluginUuid = WatchStatePlugin.BUILT_IN_WATCH_STATE_UUID.toString(),
    instances = apps.filter { it.type != AppType.Plugin }.map { app ->
        SourceInstance(
            instanceId = app.id.toString(),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to text(app.title),
                WatchStatePlugin.PROPERTY_TYPE to text(app.type.code),
            ),
        )
    },
)

private fun text(value: String) = mapOf(SourceShapeNames.SHORT_TEXT to Json.encodeToJsonElement(ShortTextShape(value)))

private fun schema(properties: String, required: List<String> = emptyList()): JsonObject {
    val requiredJson = required.joinToString(",") { "\"$it\"" }
    return Json.decodeFromString(
        JsonObject.serializer(),
        """{"type":"object","properties":{$properties},"required":[$requiredJson]}""",
    )
}
