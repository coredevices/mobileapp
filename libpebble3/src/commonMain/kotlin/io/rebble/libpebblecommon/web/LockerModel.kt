package io.rebble.libpebblecommon.web

import io.rebble.libpebblecommon.permissions.PermissionDecisionRecord
import kotlinx.serialization.KSerializer
import io.rebble.libpebblecommon.plugin.PluginPermission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.uuid.Uuid

public data class LockerModelWrapper(
    val locker: LockerModel,
    val failedToFetchUuids: Set<Uuid>,
    /** The user's decisions from other devices and the web store, by app; newest wins. */
    val permissions: Map<Uuid, Map<String, PermissionDecisionRecord>> = emptyMap(),
)

@Serializable
public data class LockerModel(
    val applications: List<LockerEntry>,
    val nextPageURL: String? = null,
)

@Serializable
public data class LockerAddResponse internal constructor(
    val application: LockerEntry,
)

@Serializable
public data class LockerEntry(
    val id: String,
    val uuid: String,
    @SerialName("user_token") val userToken: String? = null,
    val title: String,
    val type: String,
    val category: String,
    val version: String? = null,
    val hearts: Int,
    @SerialName("is_configurable") val isConfigurable: Boolean,
    @SerialName("is_timeline_enabled") val isTimelineEnabled: Boolean?,
    val links: LockerEntryLinks,
    val developer: LockerEntryDeveloper,
    @SerialName("hardware_platforms") val hardwarePlatforms: List<LockerEntryPlatform>,
    val compatibility: LockerEntryCompatibility,
    val capabilities: List<String>? = null,
    val companions: LockerEntryCompanions,
    val pbw: LockerEntryPBW? = null,
    val source: String? = null,
    // Might not be present from all stores. Absent means none of the appinfo fields below were sent.
    @SerialName("manifest_version") val manifestVersion: Int? = null,
    @SerialName("plugin_manifest") val pluginManifest: JsonObject? = null,
    @SerialName("config_page") val configPage: String? = null,
    @SerialName("icon_tintable") val iconTintable: Boolean = false,
    @SerialName("uses_permissions") val usesPermissions: List<PluginPermission>? = null,
    @SerialName("pebblekit2_packages") val pebbleKit2Packages: List<String>? = null,
)

@Serializable
public data class LockerEntryLinks(
    val remove: String,
    val href: String,
    val share: String
)

@Serializable
public data class LockerEntryDeveloper(
    val id: String,
    val name: String,
    @SerialName("contact_email") val contactEmail: String
)

@Serializable
public data class LockerEntryPlatform(
    @SerialName("sdk_version") val sdkVersion: String,
    @SerialName("pebble_process_info_flags") val pebbleProcessInfoFlags: Int,
    val name: String,
    val description: String,
    val images: LockerEntryPlatformImages
)

@Serializable
public data class LockerEntryPlatformImages(
    val icon: String,
    val list: String,
    val screenshot: String
)

@Serializable
public data class LockerEntryCompatibility internal constructor(
    val ios: LockerEntryCompatibilityPhonePlatformDetails,
    val android: LockerEntryCompatibilityPhonePlatformDetails,
    val aplite: LockerEntryCompatibilityWatchPlatformDetails,
    val basalt: LockerEntryCompatibilityWatchPlatformDetails,
    val chalk: LockerEntryCompatibilityWatchPlatformDetails,
    val diorite: LockerEntryCompatibilityWatchPlatformDetails,
    val emery: LockerEntryCompatibilityWatchPlatformDetails,
    val flint: LockerEntryCompatibilityWatchPlatformDetails? = null,
    val gabbro: LockerEntryCompatibilityWatchPlatformDetails? = null,
)

@Serializable
public data class LockerEntryCompatibilityPhonePlatformDetails internal constructor(
    val supported: Boolean,
    @SerialName("min_js_version") val minJsVersion: Int? = null
)

/**
 * Deserializer for has_binary which can be a boolean or an empty object {} in the API response.
 */
private object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return (element as? JsonPrimitive)?.booleanOrNull
        }
        return decoder.decodeBoolean()
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value != null) encoder.encodeBoolean(value)
    }
}

@Serializable
public data class LockerEntryCompatibilityWatchPlatformDetails internal constructor(
    val supported: Boolean,
    val firmware: LockerEntryFirmwareVersion,
    @SerialName("has_binary")
    @Serializable(with = LenientBooleanSerializer::class)
    val hasBinary: Boolean? = null,
)

@Serializable
public data class LockerEntryFirmwareVersion internal constructor(
    val major: Int,
    val minor: Int? = null,
    val patch: Int? = null
)

@Serializable
public data class LockerEntryCompanions internal constructor(
    val android: LockerEntryCompanionApp?,
    val ios: LockerEntryCompanionApp?,
)

@Serializable
public data class LockerEntryCompanionApp internal constructor(
    val id: Int,
    val icon: String,
    val name: String,
    val url: String? = null,
    val required: Boolean,
    @SerialName("pebblekit_version") val pebblekitVersion: String
)

@Serializable
public data class LockerEntryPBW(
    val file: String,
    @SerialName("icon_resource_id") val iconResourceId: Int,
    @SerialName("release_id") val releaseId: String
)
