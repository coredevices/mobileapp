package coredevices.coreapp.push

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

actual class FirebaseFCMService() : FirebaseMessagingService() {
    private val logger = Logger.withTag("FirebaseFCMService")
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pinSyncService: PinSyncService by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        logger.i { "FCM message received from: ${message.from}" }
        logger.d { "Message ID: ${message.messageId}" }

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
            logger.d { "Not handling notifications from fcm title: ${notification.title}" }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        logger.i { "FCM token refreshed: $token" }
    }

    /**
     * Process FCM message by triggering sync from localhost
     */
    private suspend fun processMessage(message: RemoteMessage) {
        logger.d { "Processing FCM message data: ${message.data}" }
        pinSyncService.sync()
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d { "FirebaseFCMService destroyed" }
        serviceScope.coroutineContext.cancel()
        pinSyncService.close()
    }

    /**
     * Handle when app is deleted from server
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        logger.w { "FCM messages deleted from server" }
    }
}