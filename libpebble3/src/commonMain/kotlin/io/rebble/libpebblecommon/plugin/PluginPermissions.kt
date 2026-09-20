package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * A permission, and whatever arguments narrow it: `Internet` for one domain rather than for the
 * web at large. Written in a manifest either as the bare name or as an object, and always read
 * back as an object, so a consumer never has to handle both.
 *
 * ```jsonc
 * "usesPermissions": [
 *   "LocalNetwork",
 *   { "name": "Internet", "parameters": { "domains": ["query1.finance.yahoo.com"] } }
 * ]
 * ```
 */
@Serializable(with = PluginPermissionSerializer::class)
data class PluginPermission(
    val name: String,
    val parameters: Map<String, List<String>> = emptyMap(),
)

@Serializable
private data class PermissionObject(
    val name: String,
    val parameters: Map<String, List<String>> = emptyMap(),
)

internal object PluginPermissionSerializer : KSerializer<PluginPermission> {
    override val descriptor: SerialDescriptor = PermissionObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): PluginPermission {
        val input = decoder as? JsonDecoder ?: return PluginPermission(decoder.decodeString())
        val element = input.decodeJsonElement()
        if (element is JsonPrimitive) return PluginPermission(element.content)
        val parsed = input.json.decodeFromJsonElement(PermissionObject.serializer(), element)
        return PluginPermission(parsed.name, parsed.parameters)
    }

    override fun serialize(encoder: Encoder, value: PluginPermission) {
        PermissionObject.serializer()
            .serialize(encoder, PermissionObject(value.name, value.parameters))
    }
}

/** Permission names a manifest can declare. Only the network ones are enforced today. */
object PluginPermissions {
    const val INTERNET = "Internet"
    const val LOCAL_NETWORK = "LocalNetwork"
    const val LOCATION = "Location"
    const val NOTIFICATIONS = "Notifications"
    const val CALENDAR = "Calendar"
    const val HEALTH = "Health"
    const val CONTACTS = "Contacts"

    const val PARAM_DOMAINS = "domains"
}

/**
 * What a caller must hold to read this source. The source guards itself: a built-in plugin is
 * the root of the data it serves — nobody grants `Calendar` to the calendar — so the permission
 * it is named after is a requirement of whoever reads it, not of the plugin.
 */
fun requiredOfCaller(source: SourceDeclaration): Set<String> =
    source.callerPermissions.map { it.name }.toSet()

fun requiredOfCaller(action: ActionDeclaration): Set<String> =
    action.callerPermissions.map { it.name }.toSet()

/** What the caller declared, by name. */
fun grantedToCaller(caller: List<PluginPermission>): Set<String> = caller.map { it.name }.toSet()
