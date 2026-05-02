package io.rebble.libpebblecommon.metadata.pbw.appinfo

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
     * If present, this watchapp is registered as an OS-level share target.
     * On Android this surfaces the watchapp as a Sharing Shortcut in the
     * system share sheet; tapping it routes the shared payload to the
     * watchapp's PKJS via a `'shareintent'` event.
     */
    val shareTarget: ShareTarget? = null,
)

/**
 * Declares that the containing watchapp can receive shared content from
 * other apps. Read from `package.json` at PBW build time.
 */
@Serializable
data class ShareTarget(
    /** MIME types the watchapp accepts. Currently only "text/plain" is honored. */
    val mimeTypes: List<String> = listOf("text/plain"),
    /** Optional display label override; defaults to [PbwAppInfo.shortName]. */
    val label: String? = null,
)
