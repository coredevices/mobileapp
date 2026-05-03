package io.rebble.libpebblecommon.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.metadata.pbw.appinfo.NotificationSubscription
import io.rebble.libpebblecommon.metadata.pbw.appinfo.NotificationSubscription.Field
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Converts an Android [StatusBarNotification] into the JSON shape PKJS
 * watchapps consume via `'appnotification'` events / `Pebble.getActiveNotifications`.
 *
 * Field extraction is gated per-watchapp by [NotificationSubscription.fields].
 * Each subscribed watchapp may request a different subset; extraction work
 * (BigPicture downsampling, MediaSession lookup, RemoteViews traversal,
 * etc.) is skipped entirely for watchapps that didn't ask. Notification-
 * level metadata (`package`, `posted`, `key`, `postTime`, `groupKey`) is
 * always emitted regardless of `fields` — watchapps need it to interpret
 * the rest of the payload.
 *
 * Non-primitive entries in [Notification.extras] (Parcelables, RemoteInputs,
 * Bitmaps, byte arrays) are skipped from the [Field.EXTRAS] output — they
 * don't survive JSON crossing and are usually too large for Bluetooth
 * anyway. Apps that hide structured data in extras as well-defined
 * primitive keys (e.g. older Maps versions exposed a navigation-state
 * int) come through unchanged.
 */
internal object WatchappNotificationSerializer {

    private val logger = Logger.withTag("WatchappNotificationSerializer")

    /**
     * @param sbn The OS notification.
     * @param posted true if just posted, false if removed (cleared).
     * @param context Used to resolve drawables for icon and RemoteViews
     *   extraction. The listener service IS a Context; pass `this` from
     *   the listener call sites. Pass null on platforms where icon
     *   extraction isn't supported (e.g. iOS receiving side, when
     *   added) — icon-shaped fields will simply be omitted.
     * @param subscription The watchapp's subscription, including its
     *   requested fields. Used to gate extraction.
     */
    fun serialize(
        sbn: StatusBarNotification,
        posted: Boolean,
        context: Context?,
        subscription: NotificationSubscription,
    ): String {
        val n: Notification = sbn.notification
        val extras: Bundle = n.extras ?: Bundle.EMPTY
        val fields = subscription.fields

        // Diagnostic: surface the parsed fields set so we can confirm
        // per-watchapp opt-in is reaching the serializer correctly. Fires
        // once per dispatched notification, at trace level.
        logger.v {
            "serialize ${sbn.packageName} posted=$posted fields=$fields"
        }

        return buildJsonObject {
            // --- Always-included notification-level metadata ----------
            // These are the keys watchapps need to interpret the rest of
            // the payload. They're cheap (no extraction work) so we
            // always emit them regardless of `fields` opt-in.
            put("package", sbn.packageName)
            put("posted", posted)
            put("key", sbn.key)
            put("postTime", sbn.postTime)
            put("groupKey", sbn.groupKey)

            // --- Text content fields ----------------------------------
            if (Field.TITLE in fields) {
                put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            }
            if (Field.TEXT in fields) {
                put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            }
            if (Field.SUB_TEXT in fields) {
                put("subText", extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
            }
            if (Field.INFO_TEXT in fields) {
                put("infoText", extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString())
            }

            // --- Semantic ---------------------------------------------
            if (Field.CATEGORY in fields) {
                put("category", n.category)
            }

            // --- Standard icons (smallIcon / largeIcon) ---------------
            // Skipped on removal events (icon adds no signal there) and
            // when no Context is available. Only extract icons the
            // watchapp actually asked for — saves a Drawable.draw() pass
            // per skipped icon.
            val wantsSmall = Field.SMALL_ICON_BASE64 in fields
            val wantsLarge = Field.LARGE_ICON_BASE64 in fields
            if ((wantsSmall || wantsLarge) && posted && context != null) {
                val icons: NotificationIconExtractor.Icons = try {
                    NotificationIconExtractor.extract(
                        context = context,
                        notification = n,
                        wantSmall = wantsSmall,
                        wantLarge = wantsLarge,
                    )
                } catch (e: Exception) {
                    logger.v(e) { "icon extract threw, continuing without icons" }
                    NotificationIconExtractor.Icons.EMPTY
                }
                if (wantsSmall) put("smallIconBase64", icons.smallIconBase64)
                if (wantsLarge) put("largeIconBase64", icons.largeIconBase64)
            } else {
                if (wantsSmall) put("smallIconBase64", null as String?)
                if (wantsLarge) put("largeIconBase64", null as String?)
            }

            // --- Actions ----------------------------------------------
            if (Field.ACTIONS in fields) {
                putJsonArray("actions") {
                    val actions = n.actions
                    if (actions != null) {
                        for (action in actions) {
                            addJsonObject {
                                put("title", action.title?.toString())
                                // The PendingIntent itself can't be
                                // exercised remotely from the watch in
                                // v1 — surfacing presence so watchapps
                                // can render "actions available" hints.
                                put("hasIntent", action.actionIntent != null)
                            }
                        }
                    }
                }
            }

            // --- Notification.extras (primitive whitelist) ------------
            if (Field.EXTRAS in fields) {
                putJsonObject("extras") {
                    encodeExtras(extras)
                }
            }

        }.toString()
    }

    /**
     * Walk [extras]' primitive entries into the surrounding JSON object.
     * Non-primitive types (Parcelables, Bitmaps, RemoteInput, byte
     * arrays) are dropped — they don't survive JSON crossing and are
     * typically too large for Bluetooth anyway. Logged at trace because
     * notifications routinely have these (e.g. EXTRA_LARGE_ICON).
     */
    private fun JsonObjectBuilder.encodeExtras(extras: Bundle) {
        for (key in extras.keySet()) {
            val jsonValue = encodeExtra(extras, key) ?: continue
            // Can't use put(key, JsonElement) directly because
            // JsonObjectBuilder doesn't accept arbitrary JsonElement —
            // route through the type-specific puts to keep
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
            else -> {
                logger.v { "skipping non-primitive extra '$key' (${value::class.simpleName})" }
                null
            }
        }
    }
}
