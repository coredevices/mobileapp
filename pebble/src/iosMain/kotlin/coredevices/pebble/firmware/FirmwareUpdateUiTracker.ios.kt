package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
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
    val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
    val identifier = key.toString()
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier,
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