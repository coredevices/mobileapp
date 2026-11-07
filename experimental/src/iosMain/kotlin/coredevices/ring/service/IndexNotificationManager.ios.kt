package coredevices.ring.service

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.ring.agent.ShortcutActionHandler
import coredevices.util.WatchNotificationAction
import coredevices.util.WatchNotifier
import coredevices.util.setWatchNotificationActionHandler
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIApplication
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PlatformIndexNotificationManager(
    private val watchNotifier: WatchNotifier,
    private val libPebble: LibPebble,
    /** Lazy: it depends on this, through AgentUserConfirmation. */
    private val shortcutActionHandler: Lazy<ShortcutActionHandler>,
) {
    val userNotificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    private fun getRegisteredCategories(): Set<*> {
        return runBlocking {
            suspendCoroutine { cont ->
                userNotificationCenter.getNotificationCategoriesWithCompletionHandler { categories ->
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(categories as Set<*>? ?: emptySet<Any>())
                }
            }
        }
    }

    private fun getDeliveredNotifications(): List<String> {
        return runBlocking {
            suspendCoroutine { cont ->
                userNotificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
                    @Suppress("UNCHECKED_CAST")
                    notifications as List<UNNotification>?
                    cont.resume(notifications?.map { it.request.identifier } ?: emptyList())
                }
            }
        }
    }

    actual fun notify(notification: GenericNotification) {
        val id = "idxnotif-${notification.id}"
        Logger.e { "Posting notification delegate ${userNotificationCenter.delegate?.debugDescription}" }
        val existingNotifications = getDeliveredNotifications()
        if (existingNotifications.contains(id)) {
            userNotificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(id))
        }
        val content = UNMutableNotificationContent()
        content.setTitle(notification.title)
        if (notification.contentText != null) {
            content.setBody(notification.contentText)
        }
        content.setSound(UNNotificationSound.defaultSound)
        // Set userInfo even when there are no actions, so tapping the
        // notification itself still deep links to the relevant view.
        val userInfo = mutableMapOf<Any?, Any?>()
        notification.actions.forEachIndexed { index, action ->
            userInfo["action-$index-deepLink"] = action.deepLink
        }
        notification.deepLink?.let { userInfo["notification-deepLink"] = it }
        if (userInfo.isNotEmpty()) {
            content.setUserInfo(userInfo)
        }
        if (notification.actions.isNotEmpty()) {
            val categoryId = "idxnotif-actions-${notification.id}"
            val unActions = notification.actions.mapIndexed { index, action ->
                UNNotificationAction.actionWithIdentifier(
                    identifier = "action-$index",
                    title = action.title,
                    options = if (action.background) 0u else UNNotificationActionOptionForeground,
                )
            }
            val category = UNNotificationCategory.categoryWithIdentifier(
                identifier = categoryId,
                actions = unActions,
                intentIdentifiers = emptyList<String>(),
                options = 0u
            )
            val existingCategories = getRegisteredCategories()
            userNotificationCenter.setNotificationCategories(existingCategories + setOf(category))
            content.setCategoryIdentifier(categoryId)
        }
        val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(
            identifier = id,
            content = content,
            trigger = null
        )
        userNotificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                println("Error posting notification: ${error.localizedDescription}")
            }
        }
        if (!notification.localOnly) {
            // Foreground actions need the app's UI, which the watch can't bring up.
            val watchActions = notification.actions.filter { it.background }.map { action ->
                WatchNotificationAction(action.title, DEEP_LINK_WATCH_ACTION, action.deepLink)
            }
            watchNotifier.send(id, notification.title, notification.contentText, watchActions)
        }
        // ponytail: best-effort — iOS has no OS-level notification timeout, so this
        // only fires while the app is alive (timer suspends in background until resume)
        notification.timeoutAfter?.let { timeout ->
            dispatch_after(
                dispatch_time(DISPATCH_TIME_NOW, timeout.inWholeNanoseconds),
                dispatch_get_main_queue()
            ) {
                userNotificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(id))
                watchNotifier.remove(id)
            }
        }
    }

    fun registerWatchActions() {
        libPebble.setWatchNotificationActionHandler(DEEP_LINK_WATCH_ACTION) { deepLink ->
            check(shortcutActionHandler.value.handleDeepLink(Uri.parse(requireNotNull(deepLink)))) { "Unhandled deep link $deepLink" }
        }
    }

    actual fun cancel(notificationId: Int) {
        val id = "idxnotif-$notificationId"
        userNotificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(id))
        watchNotifier.remove(id)
    }
}

private const val DEEP_LINK_WATCH_ACTION = "index_deep_link"
