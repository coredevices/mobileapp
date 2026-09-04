package io.rebble.libpebblecommon.plugin

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.LruCache
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.imaging.encodeForWatch
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.AndroidPebbleNotificationListenerConnection
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.isGroupSummary
import io.rebble.libpebblecommon.notification.appIconBitmap
import io.rebble.libpebblecommon.util.stripBidiIsolates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
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
class NotificationsPlugin(
    private val connection: AndroidPebbleNotificationListenerConnection,
    private val notificationAppDao: NotificationAppRealDao,
    private val appContext: AppContext,
) : Plugin {
    override val pluginUuid: Uuid = BUILT_IN_NOTIFICATIONS_UUID
    override val name: String = "Notifications"

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_NOTIFICATION),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to TEXT_SHAPES,
                PROPERTY_BODY to TEXT_SHAPES,
                PROPERTY_APP to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_APP_ICON to listOf(SourceShapeNames.IMAGE),
            ),
            supportsMultiple = true,
            usesPermissions = listOf(PluginPermission(PERMISSION)),
            suggestedRefreshIntervalSec = 30,
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        val iconSize = iconPixelSize
            ?.takeIf { properties == null || PROPERTY_APP_ICON in properties }
        return connection.shadeChanged
            .onStart { emit(Unit) }
            .map { toEnvelope(shadeContents(), iconSize) }
            .distinctUntilChanged()
    }

    private fun shadeContents(): List<ShadeNotification> =
        connection.activeNotifications()
            .orEmpty()
            .mapNotNull { it.toShadeNotification() }
            .sortedByDescending { it.postedAt }

    private suspend fun toEnvelope(
        notifications: List<ShadeNotification>,
        iconSize: IconPixelSize?,
    ) = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = notifications.map { toInstance(it, iconSize) },
    )

    private suspend fun toInstance(
        notification: ShadeNotification,
        iconSize: IconPixelSize?,
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
                put(
                    PROPERTY_APP,
                    mapOf(SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(appName))),
                )
                iconSize
                    ?.let { appIcon(notification.packageName, it) }
                    ?.let { put(PROPERTY_APP_ICON, mapOf(SourceShapeNames.IMAGE to it)) }
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun appIcon(packageName: String, size: IconPixelSize): JsonElement? {
        val key = "$packageName|${size.w}x${size.h}"
        icons.get(key)?.let { return it }
        val encoded = withContext(Dispatchers.Default) {
            appIconBitmap(packageName, appContext)?.encodeForWatch(size.w, size.h)
        } ?: return null
        val shape = encode(
            ImageShape(
                pixels = Base64.encode(encoded.pixels.toByteArray()),
                palette = Base64.encode(encoded.palette.toByteArray()),
                width = encoded.width,
                height = encoded.height,
            )
        )
        icons.put(key, shape)
        return shape
    }

    private fun textShapes(reading: String) = mapOf(
        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(reading)),
        SourceShapeNames.LONG_TEXT to encode(LongTextShape(reading)),
    )

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "notifications"
        const val ITEM_NOTIFICATION = "notification"
        const val PROPERTY_BODY = "body"
        const val PROPERTY_APP = "app"
        const val PROPERTY_APP_ICON = "app_icon"

        private const val PERMISSION = "Notifications"
        private const val MAX_ICON_CACHE_CHARS = 256 * 1024
        private val TEXT_SHAPES =
            listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.LONG_TEXT)

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_NOTIFICATIONS_UUID: Uuid =
            Uuid.parse("00000000-0000-0000-0001-000000000005")
    }
}

internal data class ShadeNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val body: String,
    val postedAt: Long,
)

internal fun StatusBarNotification.toShadeNotification(): ShadeNotification? {
    val extras = notification.extras
    return shadeNotification(
        key = key,
        packageName = packageName,
        isOngoing = isOngoing,
        isGroupSummary = notification.isGroupSummary(),
        title = extras?.getCharSequence(Notification.EXTRA_TITLE),
        text = extras?.getCharSequence(Notification.EXTRA_TEXT),
        bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
        postedAt = postTime,
    )
}

/**
 * Null for the things a user doesn't count as a notification: ongoing ones (media players,
 * foreground services) can't be dismissed, and a group summary repeats its children.
 */
internal fun shadeNotification(
    key: String,
    packageName: String,
    isOngoing: Boolean,
    isGroupSummary: Boolean,
    title: CharSequence?,
    text: CharSequence?,
    bigText: CharSequence?,
    postedAt: Long,
): ShadeNotification? {
    if (isOngoing || isGroupSummary) return null
    val cleanTitle = stripBidiIsolates(title).orEmpty()
    val cleanBody = stripBidiIsolates(bigText ?: text).orEmpty()
    if (cleanTitle.isBlank() && cleanBody.isBlank()) return null
    // A titleless notification still needs something to render as its name.
    return ShadeNotification(
        key = key,
        packageName = packageName,
        title = cleanTitle.ifBlank { cleanBody },
        body = if (cleanTitle.isBlank()) "" else cleanBody,
        postedAt = postedAt,
    )
}
