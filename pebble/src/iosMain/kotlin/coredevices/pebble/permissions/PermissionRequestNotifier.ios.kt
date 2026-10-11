package coredevices.pebble.permissions

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.uuid.Uuid

private const val CATEGORY_ID = "app-permission-request"
private const val CATEGORY_ID_ALL = "app-permission-request-all"
private const val ACTION_ALLOW = "app-permission-allow"
private const val ACTION_DENY = "app-permission-deny"
private const val USERINFO_APP_UUID = "app-permission-uuid"
private const val USERINFO_PERMISSIONS = "app-permission-names"
// Opened by IOSDelegate when the notification itself is tapped.
private const val USERINFO_DEEP_LINK = "notification-deepLink"

private val categoryLock = Mutex()
private var categoryRegistered = false

actual fun notifyPermissionRequest(appContext: AppContext, notification: PermissionRequestNotification) {
    GlobalScope.launch {
        ensureCategoryRegistered()
        val content = UNMutableNotificationContent()
        content.setTitle(notification.title)
        content.setBody(notification.body)
        content.setCategoryIdentifier(if (notification.permissions.size > 1) CATEGORY_ID_ALL else CATEGORY_ID)
        content.setUserInfo(
            mapOf(
                USERINFO_APP_UUID to notification.appUuid.toString(),
                USERINFO_PERMISSIONS to notification.permissions,
                USERINFO_DEEP_LINK to notification.deepLink,
            )
        )
        val request = UNNotificationRequest.requestWithIdentifier(notification.key.toString(), content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) Logger.w("PermissionRequestNotifier") { "Couldn't post: $error" }
        }
    }
}

actual fun removePermissionRequestNotification(appContext: AppContext, key: Int) {
    UNUserNotificationCenter.currentNotificationCenter()
        .removeDeliveredNotificationsWithIdentifiers(listOf(key.toString()))
}

/**
 * Records an answer given on a permission notification. Returns false for a response to some
 * other notification, so the caller handles it instead.
 */
fun handlePermissionNotificationResponse(
    libPebble: LibPebble,
    actionIdentifier: String,
    userInfo: Map<Any?, *>,
    onDone: () -> Unit,
): Boolean {
    val granted = when (actionIdentifier) {
        ACTION_ALLOW -> true
        ACTION_DENY -> false
        else -> return false
    }
    val appUuid = (userInfo[USERINFO_APP_UUID] as? String)?.let(Uuid::parse) ?: return false
    val permissions = (userInfo[USERINFO_PERMISSIONS] as? List<*>)?.filterIsInstance<String>() ?: return false
    GlobalScope.launch {
        try {
            answerPermissionRequests(libPebble, appUuid, permissions, granted)
        } finally {
            onDone()
        }
    }
    return true
}

/** Merges with the categories already registered, so other notifications keep their actions. */
private suspend fun ensureCategoryRegistered() = categoryLock.withLock {
    if (categoryRegistered) return@withLock
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val existing: Set<*> = suspendCoroutine { continuation ->
        center.getNotificationCategoriesWithCompletionHandler { categories ->
            continuation.resume(categories as Set<*>? ?: emptySet<Any>())
        }
    }
    val ours = setOf(CATEGORY_ID, CATEGORY_ID_ALL)
    val others = existing.filterNot { (it as? UNNotificationCategory)?.identifier in ours }.toSet()
    // Background actions: answering doesn't bring the app to the foreground.
    fun category(id: String, suffix: String) = UNNotificationCategory.categoryWithIdentifier(
        id,
        listOf(
            UNNotificationAction.actionWithIdentifier(ACTION_DENY, "Deny$suffix", 0u),
            UNNotificationAction.actionWithIdentifier(ACTION_ALLOW, "Allow$suffix", 0u),
        ),
        emptyList<String>(),
        0u,
    )
    center.setNotificationCategories(others + setOf(category(CATEGORY_ID, ""), category(CATEGORY_ID_ALL, " all")))
    categoryRegistered = true
}
