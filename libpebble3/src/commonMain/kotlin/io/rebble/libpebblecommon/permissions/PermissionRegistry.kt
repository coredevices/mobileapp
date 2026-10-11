package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class PermissionKind {
    /** Reads the user's data. */
    @SerialName("read") Read,
    /** Takes an action that could change user data. */
    @SerialName("write") Write,
    /** Lets data leave the phone. */
    @SerialName("exfiltrating") Exfiltrating,
}

data class PermissionDefinition(
    val name: String,
    val kind: PermissionKind,
)

/** No Health: the watch reads it directly, so the phone can't enforce it. */
object PermissionRegistry {
    val all: List<PermissionDefinition> = listOf(
        PermissionDefinition(PluginPermissions.INTERNET, PermissionKind.Exfiltrating),
        PermissionDefinition(PluginPermissions.LOCAL_NETWORK, PermissionKind.Exfiltrating),
        PermissionDefinition(PluginPermissions.GLOBAL_INTERNET, PermissionKind.Exfiltrating),
        PermissionDefinition(PluginPermissions.LOCATION, PermissionKind.Read),
        PermissionDefinition(PluginPermissions.NOTIFICATIONS, PermissionKind.Read),
        PermissionDefinition(PluginPermissions.CALENDAR, PermissionKind.Read),
        PermissionDefinition(PluginPermissions.CONTACTS, PermissionKind.Read),
        PermissionDefinition(PluginPermissions.TIMELINE, PermissionKind.Write),
        PermissionDefinition(PluginPermissions.READ_WATCH_SETTINGS, PermissionKind.Read),
        PermissionDefinition(PluginPermissions.WRITE_WATCH_SETTINGS, PermissionKind.Write),
        PermissionDefinition(PluginPermissions.READ_MUSIC, PermissionKind.Read),
        PermissionDefinition(PluginPermissions.CONTROL_MUSIC, PermissionKind.Write),
        PermissionDefinition(PluginPermissions.LAUNCH_APPS, PermissionKind.Write),
        PermissionDefinition(PluginPermissions.LEGACY_PEBBLEKIT, PermissionKind.Exfiltrating),
        PermissionDefinition(PluginPermissions.PEBBLEKIT_2, PermissionKind.Exfiltrating),
    )

    private val byName = all.associateBy { it.name }

    operator fun get(name: String): PermissionDefinition? = byName[name]
}

fun isLegacy(manifestVersion: Int): Boolean = manifestVersion < 2

private val LEGACY_PKJS = listOf(
    PluginPermissions.GLOBAL_INTERNET,
    PluginPermissions.LOCATION,
    PluginPermissions.TIMELINE,
)

/** What a legacy app keeps without asking, unless `askLegacyAppsForPermissions` is on. */
internal fun isLegacyImplicit(key: String, hasAndroidCompanion: Boolean): Boolean =
    key in LEGACY_PKJS || permissionOf(key) == PluginPermissions.PEBBLEKIT_2 ||
        (key == PluginPermissions.LEGACY_PEBBLEKIT && hasAndroidCompanion)

internal fun legacyRequested(hasPkjs: Boolean, hasAndroidCompanion: Boolean): List<String> = buildList {
    if (hasPkjs) addAll(LEGACY_PKJS)
    if (hasAndroidCompanion) add(PluginPermissions.LEGACY_PEBBLEKIT)
}

/** Keys are a name, `Internet/<domain>` or `PebbleKit2/<package>`. */
fun permissionOf(key: String): String = key.substringBefore('/')

private fun argumentOf(key: String): String? = key.substringAfter('/', "").ifEmpty { null }

fun domainOf(key: String): String? = argumentOf(key)?.takeIf { permissionOf(key) == PluginPermissions.INTERNET }

fun packageOf(key: String): String? = argumentOf(key)?.takeIf { permissionOf(key) == PluginPermissions.PEBBLEKIT_2 }

fun internetKey(domain: String): String = "${PluginPermissions.INTERNET}/$domain"

fun pebbleKit2Key(packageName: String): String = "${PluginPermissions.PEBBLEKIT_2}/$packageName"

fun PluginPermission.keys(): List<String> {
    if (plugin != null) return listOfNotNull(Uuid.parseOrNull(plugin)?.let { pluginPermissionKey(it, name) })
    val domains = parameters[PluginPermissions.PARAM_DOMAINS]?.takeIf { name == PluginPermissions.INTERNET }
    if (domains.isNullOrEmpty()) return listOf(name)
    return domains.map { internetKey(it.lowercase().removePrefix(".").trimEnd('.')) }
}

/** Most specific first. */
internal fun coveringKeys(key: String): List<String> {
    val domain = domainOf(key) ?: return listOf(key)
    val labels = domain.split('.')
    return labels.indices.map { internetKey(labels.drop(it).joinToString(".")) } + permissionOf(key)
}
