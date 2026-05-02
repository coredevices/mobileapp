package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Converts an Android [StatusBarNotification] into the JSON shape PKJS
 * watchapps consume via `'appnotification'` events / `Pebble.getActiveNotifications`.
 *
 * The format is intentionally close to what's available from the
 * NotificationListenerService — well-known fields surfaced at top level for
 * convenience plus a raw `extras` map for watchapps that need to dig
 * further (e.g. parsing app-specific keys like Google Maps's
 * `ongoingActivityNoti.next_step_message`).
 *
 * Non-primitive extras (Parcelables, RemoteInput, Bitmaps) are skipped from
 * the `extras` map — a watchapp that needs them would need a richer pipe
 * than JSON anyway. The notification's smallIcon and largeIcon ARE surfaced
 * separately at the top level as base64-encoded PNGs (see
 * [NotificationIconExtractor]), since they're often the most semantically
 * meaningful visual signal in a notification (e.g. Maps' turn-arrow icon).
 */
internal object WatchappNotificationSerializer {

    private val logger = Logger.withTag("WatchappNotificationSerializer")

    /**
     * @param sbn The OS notification
     * @param posted true if just posted, false if removed
     * @param context Used to resolve notification icon drawables. The
     *   listener service IS a Context; pass `this` from the listener call
     *   sites. Pass null on platforms where icon extraction isn't supported
     *   (e.g. iOS receiving side, when added) — the JSON output simply
     *   omits the icon fields.
     */
    fun serialize(sbn: StatusBarNotification, posted: Boolean, context: Context?): String {
        val n: Notification = sbn.notification
        val extras: Bundle = n.extras ?: Bundle.EMPTY

        // Best-effort icon extraction. Skipped entirely on removal events
        // (the icon adds no signal there) and when no Context is available.
        val icons: NotificationIconExtractor.Icons = if (posted && context != null) {
            try {
                NotificationIconExtractor.extract(context, n)
            } catch (e: Exception) {
                logger.v(e) { "icon extract threw, continuing without icons" }
                NotificationIconExtractor.Icons.EMPTY
            }
        } else {
            NotificationIconExtractor.Icons.EMPTY
        }

        return buildJsonObject {
            put("package", sbn.packageName)
            put("posted", posted)
            put("key", sbn.key)
            put("postTime", sbn.postTime)
            put("category", n.category)
            // Convenience surfacing — these are the keys nearly every notif
            // populates and that nearly every watchapp parser will look at.
            put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            put("subText", extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
            put("infoText", extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString())
            put("groupKey", sbn.groupKey)
            // Notification icons as base64-encoded PNGs at a fixed 32×32px.
            // Both nullable. Watchapps that don't care just ignore them; the
            // bytes are tiny (~few hundred bytes per icon) so always
            // forwarding them is cheaper than negotiating per-watchapp opt-in.
            put("smallIconBase64", icons.smallIconBase64)
            put("largeIconBase64", icons.largeIconBase64)
            putJsonObject("extras") {
                for (key in extras.keySet()) {
                    encodeExtra(extras, key)?.let { jsonValue ->
                        // We can't use put(key, JsonElement) directly because
                        // JsonObjectBuilder doesn't accept arbitrary JsonElement
                        // — we go through the type-specific puts to keep
                        // JsonNull / JsonPrimitive distinctions clean.
                        when (jsonValue) {
                            is JsonPrimitive -> when {
                                jsonValue.isString -> put(key, jsonValue.content)
                                else -> put(key, jsonValue)
                            }
                            JsonNull -> put(key, null as String?)
                            else -> put(key, jsonValue)
                        }
                    }
                }
            }
        }.toString()
    }

    @Suppress("DEPRECATION")
    private fun encodeExtra(extras: Bundle, key: String): JsonElement? {
        val value = extras.get(key) ?: return JsonNull
        return when (value) {
            is CharSequence -> JsonPrimitive(value.toString())
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value.toDouble())
            is Double -> JsonPrimitive(value)
            // Skip Parcelable, Bitmap, Icon, RemoteInput, byte[], etc. The JSON
            // representation for these would be large and useless to a JS
            // watchapp; logging at trace because notifications routinely have
            // these (e.g. EXTRA_LARGE_ICON).
            else -> {
                logger.v { "skipping non-primitive extra '$key' (${value::class.simpleName})" }
                null
            }
        }
    }
}
