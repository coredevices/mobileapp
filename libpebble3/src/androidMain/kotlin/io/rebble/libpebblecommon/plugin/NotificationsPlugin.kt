package io.rebble.libpebblecommon.plugin

import android.app.Notification
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.LruCache
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.imaging.encodeForWatch
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.AndroidPebbleNotificationListenerConnection
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.isGroupSummary
import io.rebble.libpebblecommon.notification.appIconBitmap
import io.rebble.libpebblecommon.notification.toBoundedBitmap
import io.rebble.libpebblecommon.util.stripBidiIsolates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * Built-in plugin for what is currently in the phone's notification shade, newest first. Android
 * only: iOS notifications reach the watch over ANCS without passing through the phone app at all.
 *
 * This is the shade, not what we forwarded to the watch — a notification the user has muted for
 * the watch is still in it, and one they swipe away leaves it.
 */
internal class NotificationsPlugin(
    private val connection: AndroidPebbleNotificationListenerConnection,
    private val notificationAppDao: NotificationAppRealDao,
    private val appContext: AppContext,
) : NativePlugin {
    override val pluginUuid: Uuid = BuiltInPlugins.NOTIFICATIONS
    override val name: String = "Notifications"
    override val description: String = "Notifications currently in the phone's notification shade, newest first."

    private val logger = Logger.withTag("NotificationsPlugin")

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_NOTIFICATION),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to TEXT_SHAPES,
                PROPERTY_BODY to TEXT_SHAPES,
                PROPERTY_APP to listOf(SourceShapeNames.SHORT_TEXT),
                SourceInstance.PROPERTY_PACKAGE to listOf(SourceShapeNames.LONG_TEXT),
                PROPERTY_APP_ICON to listOf(SourceShapeNames.IMAGE),
            ),
            supportsMultiple = true,
            callerPermissions = listOf(PluginPermission(PluginPermissions.NOTIFICATIONS)),
            suggestedRefreshIntervalSec = 30,
        ),
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_ONGOING),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to TEXT_SHAPES,
                PROPERTY_BODY to TEXT_SHAPES,
                PROPERTY_SUB_TEXT to TEXT_SHAPES,
                PROPERTY_APP to listOf(SourceShapeNames.SHORT_TEXT),
                SourceInstance.PROPERTY_PACKAGE to listOf(SourceShapeNames.LONG_TEXT),
                PROPERTY_PROGRESS to listOf(SourceShapeNames.NUMERIC_VALUE, SourceShapeNames.SHORT_TEXT),
                PROPERTY_APP_ICON to listOf(SourceShapeNames.IMAGE),
                PROPERTY_LARGE_ICON to listOf(SourceShapeNames.IMAGE),
            ),
            supportsMultiple = true,
            callerPermissions = listOf(PluginPermission(PluginPermissions.NOTIFICATIONS)),
            suggestedRefreshIntervalSec = 30,
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        limit: Int?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        val ongoing = item == ITEM_ONGOING
        val appIconSize = iconPixelSize
            ?.takeIf { properties == null || PROPERTY_APP_ICON in properties }
        val largeIconSize = iconPixelSize
            ?.takeIf { ongoing && (properties == null || PROPERTY_LARGE_ICON in properties) }
        return connection.shadeChanged
            .onStart { emit(Unit) }
            .map { readShade(ongoing = ongoing) }
            // Nothing rather than an empty envelope when the shade can't be read: "no
            // notifications" is a claim, and a subscriber would draw it as one.
            .filterNotNull()
            .map { toEnvelope(it, appIconSize, largeIconSize) }
            .distinctUntilChanged()
    }

    /**
     * The shade, or null if the listener isn't accepting calls — Android binds it a moment after
     * the process comes back, and a call before then throws. No retry here: the connection
     * reports a shade change when the listener binds, which re-reads.
     */
    private fun readShade(ongoing: Boolean): List<ShadeNotification>? =
        connection.activeNotifications()
            ?.mapNotNull {
                it.toShadeNotification(
                    sensitive = connection.hasSensitiveContent(it.key),
                    wantOngoing = ongoing,
                )
            }
            ?.sortedByDescending { it.postedAt }

    private suspend fun toEnvelope(
        notifications: List<ShadeNotification>,
        appIconSize: IconPixelSize?,
        largeIconSize: IconPixelSize?,
    ) = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = notifications.map { toInstance(it, appIconSize, largeIconSize) },
    )

    private suspend fun toInstance(
        notification: ShadeNotification,
        appIconSize: IconPixelSize?,
        largeIconSize: IconPixelSize?,
    ): SourceInstance {
        val appName = notificationAppDao.getEntry(notification.packageName)?.name
            ?: notification.packageName
        return SourceInstance(
            instanceId = notification.key,
            properties = buildMap {
                put(SourceInstance.PROPERTY_NAME, textShapes(notification.title))
                if (notification.body.isNotBlank()) {
                    put(PROPERTY_BODY, textShapes(notification.body))
                }
                if (notification.subText.isNotBlank()) {
                    put(PROPERTY_SUB_TEXT, textShapes(notification.subText))
                }
                put(
                    PROPERTY_APP,
                    mapOf(SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(appName))),
                )
                put(
                    SourceInstance.PROPERTY_PACKAGE,
                    mapOf(SourceShapeNames.LONG_TEXT to encode(LongTextShape(notification.packageName))),
                )
                if (notification.progress != null && notification.progressMax != null) {
                    put(PROPERTY_PROGRESS, progressShapes(notification.progress, notification.progressMax))
                }
                appIconSize
                    ?.let { appIcon(notification.packageName, it) }
                    ?.let { put(PROPERTY_APP_ICON, mapOf(SourceShapeNames.IMAGE to it)) }
                largeIconSize
                    ?.let { size -> notification.largeIcon?.let { largeIcon(it, size) } }
                    ?.let { put(PROPERTY_LARGE_ICON, mapOf(SourceShapeNames.IMAGE to it)) }
            },
        )
    }

    /**
     * An app's icon doesn't change while it's installed, so encoding it once per size outlives
     * any one shade snapshot. Bounded on encoded size rather than entry count, because a
     * subscriber may ask for anything from a 24px complication to a full-screen image.
     */
    private val icons = object : LruCache<String, JsonElement>(MAX_ICON_CACHE_CHARS) {
        override fun sizeOf(key: String, value: JsonElement): Int = value.toString().length
    }

    private suspend fun appIcon(packageName: String, size: IconPixelSize): JsonElement? {
        val key = "$packageName|${size.w}x${size.h}"
        icons.get(key)?.let { return it }
        val encoded = withContext(Dispatchers.Default) {
            appIconBitmap(packageName, appContext)?.encodeForWatch(size.w, size.h)
        } ?: return null
        val shape = encodeImage(encoded)
        icons.put(key, shape)
        return shape
    }

    private suspend fun largeIcon(
        icon: Icon,
        size: IconPixelSize,
    ): JsonElement? {
        val encoded = withContext(Dispatchers.Default) {
            try {
                icon.loadDrawable(appContext.context)?.toBoundedBitmap()?.encodeForWatch(size.w, size.h)
            } catch (e: Exception) {
                logger.w(e) { "Couldn't encode large icon" }
                null
            }
        } ?: return null
        return encodeImage(encoded)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeImage(encoded: EncodedImage): JsonElement = encode(
        ImageShape(
            pixels = Base64.encode(encoded.pixels.toByteArray()),
            palette = Base64.encode(encoded.palette.toByteArray()),
            width = encoded.width,
            height = encoded.height,
        )
    )

    private fun textShapes(reading: String) = mapOf(
        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(reading)),
        SourceShapeNames.LONG_TEXT to encode(LongTextShape(reading)),
    )

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY: String = "notifications"
        const val ITEM_NOTIFICATION: String = "notification"
        const val ITEM_ONGOING: String = "ongoing"
        const val PROPERTY_BODY: String = "body"
        const val PROPERTY_SUB_TEXT: String = "sub_text"
        const val PROPERTY_APP: String = "app"
        const val PROPERTY_PROGRESS: String = "progress"
        const val PROPERTY_APP_ICON: String = "app_icon"
        const val PROPERTY_LARGE_ICON: String = "large_icon"

        private const val MAX_ICON_CACHE_CHARS = 256 * 1024

        private val TEXT_SHAPES =
            listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.LONG_TEXT)
    }
}

internal data class ShadeNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val body: String,
    val subText: String = "",
    val progress: Int? = null,
    val progressMax: Int? = null,
    val largeIcon: Icon? = null,
    val postedAt: Long,
)

internal fun progressShapes(progress: Int, progressMax: Int): Map<String, JsonElement> {
    val pct = (progress.toLong() * 100L / progressMax).toInt()
    return mapOf(
        SourceShapeNames.NUMERIC_VALUE to Json.encodeToJsonElement(
            NumericValueShape(
                value = progress.toDouble(),
                min = 0.0,
                max = progressMax.toDouble(),
            )
        ),
        SourceShapeNames.SHORT_TEXT to Json.encodeToJsonElement(ShortTextShape("$pct%")),
    )
}

internal fun StatusBarNotification.toShadeNotification(
    sensitive: Boolean,
    wantOngoing: Boolean = false,
): ShadeNotification? {
    val extras = notification.extras
    val indeterminate = wantOngoing && extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) == true
    val progressMax = if (wantOngoing) extras?.getInt(Notification.EXTRA_PROGRESS_MAX, 0) ?: 0 else 0
    val progress = if (wantOngoing) extras?.getInt(Notification.EXTRA_PROGRESS, 0) ?: 0 else 0
    return shadeNotification(
        sensitive = sensitive,
        key = key,
        packageName = packageName,
        isOngoing = isOngoing,
        wantOngoing = wantOngoing,
        isGroupSummary = notification.isGroupSummary(),
        title = extras?.getCharSequence(Notification.EXTRA_TITLE),
        text = extras?.getCharSequence(Notification.EXTRA_TEXT),
        bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
        subText = if (wantOngoing) extras?.getCharSequence(Notification.EXTRA_SUB_TEXT) else null,
        progress = progress.takeIf { !indeterminate && progressMax > 0 },
        progressMax = progressMax.takeIf { !indeterminate && progressMax > 0 },
        largeIcon = if (wantOngoing) notification.getLargeIcon() else null,
        postedAt = postTime,
    )
}

/**
 * Null for notifications that don't match [wantOngoing] or are group summaries. A one-time
 * code, by Android's [sensitive] flag or by its look, is never published: its text is replaced.
 */
internal fun shadeNotification(
    sensitive: Boolean,
    key: String,
    packageName: String,
    isOngoing: Boolean,
    wantOngoing: Boolean = false,
    isGroupSummary: Boolean,
    title: CharSequence?,
    text: CharSequence?,
    bigText: CharSequence?,
    subText: CharSequence? = null,
    progress: Int? = null,
    progressMax: Int? = null,
    largeIcon: Icon? = null,
    postedAt: Long,
): ShadeNotification? {
    if (isOngoing != wantOngoing || isGroupSummary) return null
    val cleanTitle = stripBidiIsolates(title).orEmpty()
    val cleanBody = stripBidiIsolates(bigText ?: text).orEmpty()
    val cleanSubText = stripBidiIsolates(subText).orEmpty()
    if (cleanTitle.isBlank() && cleanBody.isBlank() && cleanSubText.isBlank()) return null
    val redact = sensitive || looksLikeOneTimeCode("$cleanTitle $cleanBody $cleanSubText")
    // A titleless notification still needs something to render as its name.
    val resolvedTitle = cleanTitle.ifBlank { cleanBody.ifBlank { cleanSubText } }
    val titleHasCode = cleanTitle.isBlank() || ONE_TIME_CODE.containsMatchIn(cleanTitle)
    val validProgress = if (progress != null && progressMax != null && progressMax > 0) {
        progress.coerceIn(0, progressMax)
    } else {
        null
    }
    return ShadeNotification(
        key = key,
        packageName = packageName,
        title = if (redact && titleHasCode) SENSITIVE_CONTENT else resolvedTitle,
        body = if (cleanTitle.isBlank()) "" else if (redact) SENSITIVE_CONTENT else cleanBody,
        subText = if (cleanSubText.isBlank()) "" else if (redact) SENSITIVE_CONTENT else cleanSubText,
        progress = validProgress,
        progressMax = if (validProgress != null) progressMax else null,
        largeIcon = if (redact) null else largeIcon,
        postedAt = postedAt,
    )
}

internal const val SENSITIVE_CONTENT = "Sensitive content"

// After Beeper's: 4-8 digits, or 3-4 and 3 split by a space or dash, standing alone; or Google's G-123456.
private val ONE_TIME_CODE =
    Regex("""(?:^|[^a-zA-Z0-9.-])(\d{3,4}[-\s]\d{3}|\d{4,8})(?:\b|\.|$)(?![a-zA-Z0-9-']|[,.;:]\s*\d)|\bG-\d{4,8}\b""")

// ponytail: English only; a code without one of these words, or in another language, gets through unless Android flags it.
private val ONE_TIME_CODE_WORDS = Regex(
    """\b(codes?|verif\w*|otp|passcodes?|passwords?|2fa|two[- ]factor|one[- ]time|security|log ?in|sign[- ]?in|pin|authenticat\w*|do not share|don'?t share)\b""",
    RegexOption.IGNORE_CASE,
)

/** A code-shaped number in text that talks about codes, so a lone order number or year isn't one. */
internal fun looksLikeOneTimeCode(text: String) = ONE_TIME_CODE_WORDS.containsMatchIn(text) && ONE_TIME_CODE.containsMatchIn(text)
