package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.NOTIFICATION_INTENT_URI_SHOW_WATCHES
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual fun notifyFirmwareUpdate(
    appContext: AppContext,
    title: String,
    body: String,
    key: Int,
    identifier: PebbleIdentifier,
) {
    val content = UNMutableNotificationContent()
    content.setTitle(title)
    content.setBody(body)
    content.setUserInfo(
        mapOf("notification-deepLink" to NOTIFICATION_INTENT_URI_SHOW_WATCHES.toString())
    )
    val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
    val notificationIdentifier = key.toString()
    val request = UNNotificationRequest.requestWithIdentifier(
        notificationIdentifier,
        content,
        trigger
    )

    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
        if (error != null) {
            Logger.w("Error scheduling firmware update notification: $error")
        } else {
            Logger.d("Firmware update notification scheduled successfully!")
        }
    }
}

actual fun removeFirmwareUpdateNotification(appContext: AppContext, key: Int) {
    val identifier = key.toString()
    UNUserNotificationCenter.currentNotificationCenter()
        .removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
}