package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Declares one notification source a watchapp wishes to subscribe to,
 * plus the set of payload fields the watchapp wants extracted and
 * forwarded for notifications from that source.
 *
 * Two ergonomic forms are accepted in `package.json`:
 *
 * 1. Bare string (legacy form, equivalent to opting into [DEFAULT_FIELDS]):
 *    ```
 *    "notificationFilter": ["com.google.android.apps.maps"]
 *    ```
 *
 * 2. Object form (explicit field opt-in):
 *    ```
 *    "notificationFilter": [
 *      { "package": "com.google.android.apps.maps",
 *        "fields": ["title", "text", "category"] }
 *    ]
 *    ```
 *
 * The two forms can be mixed within the same array — bare strings are
 * shorthand, objects are precise.
 *
 * The `fields` list controls bandwidth and CPU: only the listed fields
 * are extracted phone-side and emitted in the JSON payload pushed to
 * the watchapp's PKJS as an `'appnotification'` event. Notification-
 * level metadata (`package`, `posted`, `key`, `postTime`, `groupKey`) is
 * always included regardless of `fields` — watchapps need it to
 * interpret the rest of the payload.
 *
 * When `fields` is omitted, [DEFAULT_FIELDS] is used. This matches the
 * payload shape from before per-field opt-in existed, so watchapps that
 * worked under the old schema continue to work unchanged.
 *
 * See [Field] for the v1 set of recognized field names.
 */
@Serializable(with = NotificationSubscription.Serializer::class)
data class NotificationSubscription(
    /** Android package name of the source app (e.g. `"com.google.android.apps.maps"`). */
    val packageName: String,
    /**
     * Set of [Field] names the watchapp wants extracted from each
     * matching notification. Names not in [Field.ALL_NAMES] are silently
     * ignored (forward compatibility — a future libpebble3 may add
     * fields a current watchapp doesn't know about, and vice versa).
     */
    val fields: Set<String> = DEFAULT_FIELDS,
) {
    companion object {
        /**
         * Field set delivered when a subscription is declared without an
         * explicit `fields` array. Chosen to match the payload shape
         * delivered by libpebble3 versions before per-field opt-in
         * existed, so legacy `notificationFilter: ["pkg"]` declarations
         * receive exactly the same fields they did before.
         */
        val DEFAULT_FIELDS: Set<String> = setOf(
            Field.TITLE,
            Field.TEXT,
            Field.SUB_TEXT,
            Field.INFO_TEXT,
            Field.CATEGORY,
            Field.SMALL_ICON_BASE64,
            Field.LARGE_ICON_BASE64,
            Field.EXTRAS,
        )
    }

    /**
     * Catalog of v1 field names a watchapp may declare. Adding a field
     * here is the only schema change needed to introduce a new
     * extractor; the platform-side serializer reads `fields` and emits
     * each requested name's payload independently.
     *
     * Field names are stable and forward-compatible: a watchapp built
     * against a newer libpebble3 declaring `"mediaMetadata"` will still
     * load on an older libpebble3 (it just won't get that field in
     * payloads).
     */
    object Field {
        // --- Text content (extras.getCharSequence based) ---

        /** Notification's primary title (`Notification.EXTRA_TITLE`). */
        const val TITLE = "title"

        /** Notification's main text body (`Notification.EXTRA_TEXT`). */
        const val TEXT = "text"

        /** Smaller secondary text line (`Notification.EXTRA_SUB_TEXT`). */
        const val SUB_TEXT = "subText"

        /** Auxiliary info shown to the right (`Notification.EXTRA_INFO_TEXT`). */
        const val INFO_TEXT = "infoText"

        // --- Semantic ---

        /**
         * Notification category constant such as `"navigation"`, `"call"`,
         * `"msg"`, `"transport"`, `"alarm"`, etc. Useful as a cheap
         * dispatch hint for watchapps subscribed to multiple packages.
         */
        const val CATEGORY = "category"

        // --- Standard Android icons ---

        /**
         * Notification's `smallIcon` rasterized to a 32×32 ARGB PNG and
         * base64-encoded. On many apps (Google Maps included) this is
         * the brand glyph rather than turn-specific iconography; apps
         * that need per-state dynamic icons typically aren't accessible
         * via this slot.
         */
        const val SMALL_ICON_BASE64 = "smallIconBase64"

        /**
         * Notification's `largeIcon` rasterized to a 32×32 ARGB PNG and
         * base64-encoded. Used by music apps for compact album art and
         * by chat apps for sender avatars.
         */
        const val LARGE_ICON_BASE64 = "largeIconBase64"

        // --- BigPictureStyle / MediaStyle / MessagingStyle / InboxStyle ---

        /**
         * The full-resolution photo from a `BigPictureStyle` notification,
         * downsampled to fit a configured byte cap (default 8KB) and
         * base64-encoded. This is where photo apps (Photos, gallery,
         * Instagram-style notifications) and some music apps put album
         * art at higher resolution than [LARGE_ICON_BASE64].
         */
        const val BIG_PICTURE_BASE64 = "bigPictureBase64"

        /**
         * Structured media metadata from the `MediaSession` associated
         * with a `MediaStyle` notification: `{ title, artist, album,
         * durationMs, positionMs, playbackState, albumArtBase64 }`.
         * `albumArtBase64` is omitted if larger than the cap (default
         * 8KB after downsampling). All fields nullable individually.
         */
        const val MEDIA_METADATA = "mediaMetadata"

        /**
         * Array of messages from a `MessagingStyle` notification, each
         * `{ sender, timestamp, text }`. Messaging apps (Signal, WhatsApp,
         * SMS, etc.) deliver conversation context this way; the
         * notification's [TEXT] field typically carries only the most
         * recent message.
         */
        const val MESSAGING_MESSAGES = "messagingMessages"

        /**
         * Array of strings from an `InboxStyle` notification — used by
         * email/news apps showing N unread items. Each entry is a
         * single line as the source app intends it.
         */
        const val INBOX_LINES = "inboxLines"

        // --- Interaction surface ---

        /**
         * Array of `{ title, hasIntent }` for each declared notification
         * action button. PendingIntents themselves aren't forwarded
         * (can't be exercised remotely from the watch in v1), but the
         * presence flag lets watchapps surface "this notification has
         * actions" affordances.
         */
        const val ACTIONS = "actions"

        // --- App-defined data ---

        /**
         * The `Notification.extras` bundle's primitive entries
         * (CharSequence / String / Boolean / numeric types) merged into
         * a flat map. Non-primitive entries (Parcelables, Bitmaps,
         * RemoteInputs, byte arrays) are stripped — they don't survive
         * JSON crossing and are usually too large for Bluetooth anyway.
         * Apps occasionally publish well-defined integer codes in
         * extras (e.g. older Maps versions exposed a navigation-state
         * int); subscribing to [EXTRAS] is the way to read them.
         */
        const val EXTRAS = "extras"

        /** All recognized field names. Used for forward-compat filtering. */
        val ALL_NAMES: Set<String> = setOf(
            TITLE, TEXT, SUB_TEXT, INFO_TEXT,
            CATEGORY,
            SMALL_ICON_BASE64, LARGE_ICON_BASE64,
            BIG_PICTURE_BASE64, MEDIA_METADATA,
            MESSAGING_MESSAGES, INBOX_LINES,
            ACTIONS,
            EXTRAS,
        )
    }

    /**
     * Polymorphic serializer accepting either bare-string or object
     * form in JSON. On encode we always emit object form for
     * round-trip stability.
     */
    object Serializer : KSerializer<NotificationSubscription> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("NotificationSubscription")

        override fun deserialize(decoder: Decoder): NotificationSubscription {
            val jsonDecoder = decoder as? JsonDecoder
                ?: error("NotificationSubscription requires Json")
            return when (val element: JsonElement = jsonDecoder.decodeJsonElement()) {
                is JsonPrimitive -> {
                    require(element.isString) {
                        "notificationFilter entry must be a string or object, got: $element"
                    }
                    NotificationSubscription(packageName = element.content)
                }
                is JsonObject -> {
                    val pkg = element["package"]?.jsonPrimitive?.content
                        ?: error("notificationFilter object missing required 'package' field")
                    val fields = element["fields"]
                        ?.jsonArray
                        ?.mapTo(mutableSetOf()) { it.jsonPrimitive.content }
                        ?: DEFAULT_FIELDS.toMutableSet()
                    NotificationSubscription(packageName = pkg, fields = fields)
                }
                is JsonArray -> error(
                    "notificationFilter entry must be a string or object, got array: $element"
                )
            }
        }

        override fun serialize(encoder: Encoder, value: NotificationSubscription) {
            val jsonEncoder = encoder as? JsonEncoder
                ?: error("NotificationSubscription requires Json")
            jsonEncoder.encodeJsonElement(buildJsonObject {
                put("package", JsonPrimitive(value.packageName))
                put(
                    "fields",
                    JsonArray(value.fields.map { JsonPrimitive(it) })
                )
            })
        }
    }
}
