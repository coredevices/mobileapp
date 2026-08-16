package coredevices.coreapp.api

import CommonRoutes
import co.touchlab.kermit.Logger
import coredevices.coreapp.ui.navigation.CoreDeepLinkHandler.Companion.asUri
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.uuid.Uuid
import PlatformContext

actual fun createNotification(
    platformContext: PlatformContext,
    title: String,
    message: String,
    conversationId: String
) {
    val notifCenter = UNUserNotificationCenter.currentNotificationCenter()
    val content = UNMutableNotificationContent().apply {
        setTitle("Pebble Support message")
        setBody(message)
        setUserInfo(
            mapOf(
                "notification-deepLink" to CommonRoutes.ViewBugReportRoute(conversationId).asUri().toString()
            )
        )
    }
    val request = UNNotificationRequest.requestWithIdentifier(
        Uuid.random().toString(),
        content,
        null
    )
    notifCenter.addNotificationRequest(request) { error ->
        if (error != null) {
            Logger.e("createNotification") { "Error adding notification request: $error" }
        } else {
            Logger.d("createNotification") { "Notification request added successfully" }
        }
    }
}
