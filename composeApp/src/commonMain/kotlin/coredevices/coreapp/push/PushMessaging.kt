package coredevices.coreapp.push

import CoreAppVersion
import PlatformContext
import co.touchlab.kermit.Logger
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.PayloadData
import coredevices.coreapp.api.BugReports
import coredevices.coreapp.api.PushService
import coredevices.coreapp.api.PushTokenRequest
import coredevices.pebble.Platform
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

class PushMessaging(
    private val pushService: PushService,
    private val platform: Platform,
    private val appVersion: CoreAppVersion,
    private val bugReports: BugReports,
    private val platformContext: PlatformContext,
) {
    private val logger = Logger.withTag("PushMessaging")

    fun init() {
        NotifierManager.addListener(object : NotifierManager.Listener {
            override fun onNewToken(token: String) {
                logger.v { "onNewToken" }
                GlobalScope.launch {
                    uploadToken(token)
                }
            }

            override fun onPushNotification(title: String?, body: String?) {
                logger.v { "onPushNotification: title=$title body=$body" }
            }

            override fun onPushNotificationWithPayloadData(
                title: String?,
                body: String?,
                data: PayloadData,
            ) {
                handleMessage(data)
            }
        })
        NotifierManager.setLogger { message -> Logger.v(message) }

        GlobalScope.launch {
            Firebase.auth.authStateChanged.drop(1).collect { auth ->
                logger.d { "Auth changed" }
                findTokenAndUpload()
            }
        }
    }

    private suspend fun findTokenAndUpload() {
        val fcmToken = getFcmToken()
        logger.e { "token = $fcmToken" }
        if (fcmToken == null) {
            logger.e { "Failed to get push token" }
            return
        }
        uploadToken(fcmToken)
    }

    private suspend fun uploadToken(fcmToken: String) {
        logger.v { "uploadToken" }
        val userIdToken = try {
            Firebase.auth.currentUser?.getIdToken(false)
        } catch (e: Exception) {
            logger.e(e) { "No user token: ${e.message}" }
            null
        }

        val email = Firebase.auth.currentUser?.emailOrNull
        if (userIdToken == null || email == null) {
            logger.e { "Failed to get user id token/email" }
            return
        }

        val request = try {
            PushTokenRequest(
                email = email,
                push_token = fcmToken,
                platform = when (platform) {
                    Platform.IOS -> "ios"
                    Platform.Android -> "android"
                },
                device_id = platformContext.getDeviceId(),
                app_version = appVersion.version,
            )
        } catch (e: IllegalStateException) {
            logger.e(e) { "Failed to create PushTokenRequest: ${e.message}" }
            return
        }
        pushService.uploadPushToken(request, userIdToken)
    }

    private fun handleMessage(data: PayloadData) {
        val type = data["type"]
        logger.d { "handleMessage: type=$type data=$data" }
        when (type) {
            "atlas_message" -> {
                val title = data["title"] as? String?
                val body = data["body"] as? String?
                val conversationId = data["conversationId"] as? String?
                val timestamp = data["timestamp"] as? String?
                if (title == null) {
                    logger.e { "title is null for atlas_message" }
                    return
                }
                if (body == null) {
                    logger.e { "body is null for atlas_message" }
                    return
                }
                if (conversationId == null) {
                    logger.e { "conversationId is null for atlas_message" }
                    return
                }
                if (timestamp == null) {
                    logger.e { "timestamp is null for atlas_message" }
                    return
                }
                val ts = try {
                    Instant.parse(timestamp)
                } catch (e: IllegalArgumentException) {
                    logger.e(e) { "Failed to parse timestamp: $timestamp" }
                    null
                }
                if (ts == null) {
                    return
                }
                val message = AtlasPushMessage(
                    conversationId = conversationId,
                    title = title,
                    body = body,
                    timestamp = ts,
                )
                bugReports.handlePushMessage(message)
            }
        }
    }

    private suspend fun getFcmToken(): String? {
        val token = NotifierManager.getPushNotifier().getToken()
        if (token != null) {
            return token
        }
        delay(10.seconds)
        logger.v { "retrying fcm token after 10 seconds" }
        return NotifierManager.getPushNotifier().getToken()
    }
}

data class AtlasPushMessage(
    val conversationId: String,
    val title: String,
    val body: String,
    val timestamp: Instant,
)

expect fun PlatformContext.getDeviceId(): String