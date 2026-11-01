package io.rebble.libpebblecommon.metadata.pbw.appinfo

import io.rebble.libpebblecommon.permissions.declaredPermissions
import io.rebble.libpebblecommon.permissions.isLegacy
import io.rebble.libpebblecommon.plugin.PluginManifest
import io.rebble.libpebblecommon.plugin.PluginPermission
import kotlinx.serialization.Serializable

@Serializable
data class PbwAppInfo(
    val uuid: String,
    val shortName: String,
    val longName: String = "",
    val companyName: String = "",
    val versionCode: Float = -1f,
    val versionLabel: String,
    val appKeys: Map<String, Int> = emptyMap(),
    val capabilities: List<String> = emptyList(),
    val resources: Resources,
    val sdkVersion: String = "3",
    // If list of target platforms is not present, pbw is legacy applite app
    val targetPlatforms: List<String> = listOf("aplite"),
    val watchapp: Watchapp = Watchapp(),
    val companionApp: CompanionApp? = null,
    /**
     * Settings page for the whole pbw: an http(s) URL, or a filename bundled in the pbw. When set,
     * it is preferred over the PKJS `showConfiguration` flow; when absent, that flow still applies.
     */
    val configPage: String? = null,
    /** Plugin this pbw carries, if any; its script/config are sibling entries in the pbw. */
    val plugin: PluginManifest? = null,
    /**
     * What this app and its plugin ask to be allowed to do. Declared in the `pebble` block of
     * package.json, which the SDK copies into appinfo.json verbatim.
     */
    val usesPermissions: List<PluginPermission> = emptyList(),
    /** Below 2 is a legacy app. */
    val manifestVersion: Int = 1,
) {
    val declaredPermissions: List<PluginPermission>?
        get() = declaredPermissions(usesPermissions, manifestVersion)

    /** Plugins only load from non-legacy pbws. */
    val loadablePlugin: PluginManifest?
        get() = plugin.takeUnless { isLegacy(manifestVersion) }
}
