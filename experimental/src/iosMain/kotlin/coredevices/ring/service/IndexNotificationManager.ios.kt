package coredevices.ring.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PlatformIndexNotificationManager {
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
        if (notification.actions.isNotEmpty()) {
            val categoryId = "idxnotif-actions-${notification.id}"
            val unActions = notification.actions.mapIndexed { index, action ->
                UNNotificationAction.actionWithIdentifier(
                    identifier = "action-$index",
                    title = action.title,
                    options = UNNotificationActionOptionForeground
                )
            }
            val userInfo = mutableMapOf<Any?, Any?>()
            notification.actions.forEachIndexed { index, action ->
                userInfo["action-$index-deepLink"] = action.deepLink
            }
            notification.deepLink?.let { userInfo["notification-deepLink"] = it }
            content.setUserInfo(userInfo)
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
    }

    actual fun cancel(notificationId: Int) {
        userNotificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf("idxnotif-$notificationId"))
    }
}