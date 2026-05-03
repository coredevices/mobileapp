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
    /**
     * Notification subscriptions this watchapp wants to receive. Each
     * entry names an Android source package and the set of payload
     * fields the watchapp wants extracted from notifications posted by
     * that package. Subscribed notifications are dispatched to the
     * watchapp's PKJS via an `'appnotification'` event whenever the
     * watchapp is the active foreground app on the watch (i.e. PKJS is
     * running). The user must have already granted notification access
     * to the Pebble app itself; no per-watchapp consent is required
     * because the declaration in `package.json` is treated as the
     * consent moment.
     *
     * Two shapes are accepted in `package.json`:
     *
     * - Bare string: `"com.google.android.apps.maps"` — equivalent to
     *   subscribing with [NotificationSubscription.DEFAULT_FIELDS].
     *   This is the legacy form; existing watchapps written for older
     *   libpebble3 versions continue to parse correctly.
     * - Object: `{ "package": "com.google.android.apps.maps",
     *   "fields": ["title", "text", "category"] }`
     *   — opts into a specific field set, controlling bandwidth and
     *   CPU cost on the phone side.
     *
     * Empty / absent means this watchapp does not receive notifications.
     *
     * See [NotificationSubscription.Field] for the catalog of v1 field
     * names.
     */
    val notificationFilter: List<NotificationSubscription> = emptyList(),
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
