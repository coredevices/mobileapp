package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.permissions.PermissionRegistry
import io.rebble.libpebblecommon.permissions.argumentOf
import io.rebble.libpebblecommon.permissions.keys
import io.rebble.libpebblecommon.permissions.permissionOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A permission, and whatever arguments narrow it: `Internet` for one domain rather than for the
 * web at large, `Notifications` for one app's. Written in a manifest either as the bare name or as an object, and always read
 * back as an object, so a consumer never has to handle both.
 *
 * ```jsonc
 * "usesPermissions": [
 *   "LocalNetwork",
 *   { "name": "Internet", "parameters": { "domains": ["query1.finance.yahoo.com"] } },
 *   { "name": "Notifications", "parameters": { "packages": ["com.google.android.apps.maps"] } },
 *   { "name": "HomeControl", "plugin": "<plugin uuid>" }
 * ]
 * ```
 */
@Serializable(with = PluginPermissionSerializer::class)
data class PluginPermission(
    val name: String,
    val parameters: Map<String, List<String>> = emptyMap(),
    /** Set for a permission another plugin defines. */
    val plugin: String? = null,
)

@Serializable
private data class PermissionObject(
    val name: String,
    val parameters: Map<String, List<String>> = emptyMap(),
    val plugin: String? = null,
)

internal object PluginPermissionSerializer : KSerializer<PluginPermission> {
    override val descriptor: SerialDescriptor = PermissionObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): PluginPermission {
        val input = decoder as? JsonDecoder ?: return PluginPermission(decoder.decodeString())
        val element = input.decodeJsonElement()
        if (element is JsonPrimitive) return PluginPermission(element.content)
        val parsed = input.json.decodeFromJsonElement(PermissionObject.serializer(), element)
        return PluginPermission(parsed.name, parsed.parameters, parsed.plugin)
    }

    override fun serialize(encoder: Encoder, value: PluginPermission) {
        PermissionObject.serializer()
            .serialize(encoder, PermissionObject(value.name, value.parameters, value.plugin))
    }
}

object PluginPermissions {
    const val INTERNET = "Internet"
    const val LOCAL_NETWORK = "LocalNetwork"
    /** Legacy apps only. */
    const val GLOBAL_INTERNET = "GlobalInternet"
    const val LOCATION = "Location"
    const val WATCH_MICROPHONE = "WatchMicrophone"
    const val NOTIFICATIONS = "Notifications"
    const val CALENDAR = "Calendar"
    const val CONTACTS = "Contacts"
    const val TIMELINE = "Timeline"
    const val READ_WATCH_SETTINGS = "ReadWatchSettings"
    const val WRITE_WATCH_SETTINGS = "WriteWatchSettings"
    const val READ_MUSIC = "ReadMusic"
    const val CONTROL_MUSIC = "ControlMusic"
    const val LAUNCH_APPS = "LaunchApps"
    const val FIND_MY_PHONE = "FindMyPhone"
    const val READ_FILES = "ReadFiles"
    const val WRITE_FILES = "WriteFiles"
    const val LEGACY_PEBBLEKIT = "LegacyPebbleKit"
    const val PEBBLEKIT_2 = "PebbleKit2"

    const val PARAM_DOMAINS = "domains"
    const val PARAM_PACKAGES = "packages"
}

/**
 * What a caller must hold to read this source. The source guards itself: a built-in plugin is
 * the root of the data it serves — nobody grants `Calendar` to the calendar — so the permission
 * it is named after is a requirement of whoever reads it, not of the plugin.
 */
fun requiredOfCaller(source: SourceDeclaration): Set<String> =
    source.callerPermissions.flatMap { it.keys() }.toSet()

fun requiredOfCaller(action: ActionDeclaration): Set<String> =
    action.callerPermissions.flatMap { it.keys() }.toSet()

/**
 * For each [required] permission the caller holds only for some arguments (`Notifications/<package>`),
 * the instance property those arguments match and the values it may have. Null if it lacks one.
 */
internal fun callerScope(required: Set<String>, granted: Set<String>): Map<String, Set<String>>? =
    required.filterNot { it in granted }.associate { permission ->
        val property = PermissionRegistry[permission]?.scopeProperty ?: return null
        val allowed = granted.filter { permissionOf(it) == permission }.mapNotNull(::argumentOf).toSet()
        if (allowed.isEmpty()) return null
        property to allowed
    }

/** Only the instances whose text in each of [scope]'s properties is one it allows. */
internal fun SourceUpdate.scopedTo(scope: Map<String, Set<String>>): SourceUpdate {
    if (this !is SourceUpdate.Data || scope.isEmpty()) return this
    val visible = envelope.instances.filter { instance ->
        scope.all { (property, allowed) -> instance.text(property) in allowed }
    }
    return SourceUpdate.Data(envelope.copy(instances = visible))
}

private fun SourceInstance.text(property: String): String? =
    properties[property]?.values?.firstNotNullOfOrNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.content }
