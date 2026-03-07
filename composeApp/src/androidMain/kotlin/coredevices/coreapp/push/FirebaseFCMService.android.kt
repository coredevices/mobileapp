package coredevices.coreapp.push

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

actual class FirebaseFCMService() : FirebaseMessagingService() {
    private val logger = Logger.withTag("FirebaseFCMService")
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Inject dependencies via Koin
    private val libPebble: LibPebble by inject()
    private val payloadParser: FCMPayloadParser by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        logger.i { "FCM message received from: ${message.from}" }
        logger.d { "Message ID: ${message.messageId}" }
        logger.d { "Message data keys: ${message.data.keys.joinToString(", ")}" }

        // Handle incoming message data
        if (message.data.isNotEmpty()) {
            serviceScope.launch {
                try {
                    if (message.data["type"].equals("timeline.pin.create")) {
                        processMessage(message)
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Failed to process FCM message: ${e.message}" }
                }
            }
        }

        // Handle notification payload if present
        message.notification?.let { notification ->
            logger.d { "Notification title: ${notification.title}" }
            logger.d { "Notification body: ${notification.body}" }

            // If we only have notification payload without data, create a simple message
            if (message.data.isEmpty()) {
                serviceScope.launch {
                    try {
                        processSimpleNotification(notification.title, notification.body)
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to process FCM notification: ${e.message}" }
                    }
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        logger.i { "FCM token refreshed: ${token.take(10)}..." }

        // Token handling should be managed by PushMessaging class
        // This is just for logging purposes
    }

    /**
     * Process FCM message with data payload
     */
    private suspend fun processMessage(message: RemoteMessage) {
        logger.d { "Processing FCM message data: ${message.data}" }
        logger.d { "Processing FCM message with type: ${message.data["type"]}" }
        logger.d { "Processing FCM message with title: ${message.data["pin"]}" }

        try {
            // Parse FCM data directly to timeline notification
            val timelineNotification = payloadParser.parseToTimelineNotification(message.data)

            if (timelineNotification == null) {
                logger.e { "Failed to parse FCM message to timeline notification" }
                return
            }

            logger.d {
                "Parsed FCM message to timeline notification: " +
                "title='${timelineNotification.content.layout.name}', " +
                "body='${timelineNotification.content.attributes}',"
                "actions='${timelineNotification.content.actions}'"
            }

            logger.d { "Created timeline notification with ${timelineNotification.content.attributes.size} attributes and ${timelineNotification.content.actions.size} actions" }

            // Send to connected watch
            libPebble.sendNotification(timelineNotification)

            logger.i { "Successfully sent FCM notification to timeline" }

        } catch (e: Exception) {
            logger.e(e) { "Error processing FCM message: ${e.message}" }

            // Fallback to simple notification if parsing fails
            try {
                val title = message.data["title"] ?: message.data["gcm.notification.title"]
                val body = message.data["body"] ?: message.data["gcm.notification.body"]
                val appName = message.data["appName"] ?: message.data["app_name"]

                if (!title.isNullOrEmpty() || !body.isNullOrEmpty()) {
                    logger.w { "Falling back to simple notification due to parsing error" }
                    val fallbackNotification =
                        payloadParser.createFallbackNotification(title, body, appName)
                    libPebble.sendNotification(fallbackNotification)
                    logger.i { "Sent fallback notification to timeline" }
                }
            } catch (fallbackError: Exception) {
                logger.e(fallbackError) { "Failed to send fallback notification: ${fallbackError.message}" }
            }
        }
    }

    /**
     * Process simple FCM notification without data payload
     */
    private suspend fun processSimpleNotification(title: String?, body: String?) {
        if (title.isNullOrEmpty() && body.isNullOrEmpty()) {
            logger.w { "Received FCM notification with empty title and body" }
            return
        }

        logger.d { "Processing simple FCM notification: title='$title', body='$body'" }

        try {
            val timelineNotification = payloadParser.createFallbackNotification(
                title = title,
                body = body,
                appName = "Push Notification"
            )

            libPebble.sendNotification(timelineNotification)
            logger.i { "Successfully sent simple FCM notification to timeline" }

        } catch (e: Exception) {
            logger.e(e) { "Failed to process simple FCM notification: ${e.message}" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d { "FirebaseFCMService destroyed" }
        serviceScope.coroutineContext.cancel()
    }

    /**
     * Handle message sent acknowledgment
     */
    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
        logger.d { "FCM message sent: $msgId" }
    }

    /**
     * Handle message send error
     */
    override fun onSendError(msgId: String, exception: Exception) {
        super.onSendError(msgId, exception)
        logger.e(exception) { "FCM message send error for $msgId: ${exception.message}" }
    }

    /**
     * Handle when app is deleted from server
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        logger.w { "FCM messages deleted from server" }
    }
}