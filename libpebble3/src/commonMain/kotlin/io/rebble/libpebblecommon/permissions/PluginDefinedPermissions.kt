package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.plugin.PluginManifest
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.database.entity.LockerEntry
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

data class PluginDefinedPermission(
    val key: String,
    val pluginUuid: Uuid,
    val pluginName: String,
    val label: String,
    val kind: PermissionKind,
)

fun pluginPermissionKey(pluginUuid: Uuid, name: String): String = "$pluginUuid:$name"

/** Implicit: needed to invoke a plugin's actions when the plugin can exfiltrate. */
private const val INVOKE_ACTION = "InvokeAction"

fun invokeActionKey(pluginUuid: Uuid): String = pluginPermissionKey(pluginUuid, INVOKE_ACTION)

private val PERMISSION_NAME = Regex("[A-Za-z][A-Za-z0-9]*")

/** Resolves `callerPermissions` names to keys; the plugin's own names win over built-ins. Throws if invalid. */
fun PluginManifest.resolvePermissions(pluginUuid: Uuid): PluginManifest {
    val defined = definesPermissions.map { it.name }.toSet()
    definesPermissions.forEach {
        require(PERMISSION_NAME.matches(it.name)) { "permission name '${it.name}' must be alphanumeric" }
        require(it.name != INVOKE_ACTION) { "permission name '$INVOKE_ACTION' is reserved" }
        require(it.label.isNotBlank()) { "permission '${it.name}' has no label" }
    }
    fun resolve(permissions: List<PluginPermission>) = permissions.map {
        when {
            it.name in defined -> it.copy(name = pluginPermissionKey(pluginUuid, it.name))
            PermissionRegistry[it.name] != null -> it
            else -> throw IllegalArgumentException("'${it.name}' is neither built-in nor in definesPermissions")
        }
    }
    return copy(
        sources = sources.map { it.copy(callerPermissions = resolve(it.callerPermissions)) },
        actions = actions.map { it.copy(callerPermissions = resolve(it.callerPermissions)) },
    )
}

private val storedManifestJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun pluginDefinedPermissions(plugins: List<LockerEntry>): Map<String, PluginDefinedPermission> =
    plugins.filterNot { isLegacy(it.manifestVersion) }.flatMap { entry ->
        val manifest = entry.pluginManifest?.let {
            runCatching { storedManifestJson.decodeFromString(PluginManifest.serializer(), it).resolvePermissions(entry.id) }
                .getOrNull()
        } ?: return@flatMap emptyList()
        manifest.definesPermissions.map {
            PluginDefinedPermission(pluginPermissionKey(entry.id, it.name), entry.id, entry.title, it.label, it.kind)
        } + PluginDefinedPermission(invokeActionKey(entry.id), entry.id, entry.title, "Send data off your phone", PermissionKind.Exfiltrating)
    }.associateBy { it.key }
