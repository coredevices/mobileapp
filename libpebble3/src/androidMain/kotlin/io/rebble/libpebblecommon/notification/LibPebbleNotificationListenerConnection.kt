package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.notification.LibPebbleNotificationListener
import io.rebble.libpebblecommon.notification.NotificationListenerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class AndroidPebbleNotificationListenerConnection(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val notificationHandler: NotificationHandler,
    private val notificationConfig: NotificationConfigFlow,
    private val context: Context,
) : NotificationListenerConnection {
    private val logger = Logger.withTag("AndroidPebbleNotificationListenerConnection")

    private var listenerService: LibPebbleNotificationListener? = null
    private val notificationSendQueue = notificationHandler.notificationSendQueue.consumeAsFlow()
    private val notificationDeleteQueue = notificationHandler.notificationDeleteQueue.consumeAsFlow()

    fun getNotificationAction(itemId: Uuid, actionId: UByte): LibPebbleNotificationAction? {
        return notificationHandler.getNotificationAction(itemId, actionId)
    }

    fun setService(service: LibPebbleNotificationListener?) {
        logger.d { "setService: $service" }
        listenerService = service
    }

    fun getService(): LibPebbleNotificationListener? = listenerService

    fun onListenerDisconnected() {
        libPebbleCoroutineScope.launch {
            // Small debounce: the OS often rebinds on its own, and a disconnect can be immediately
            // followed by a reconnect. Only act if we still have no service afterwards.
            delay(REBIND_DEBOUNCE)
            if (listenerService == null) requestRebindIfNeeded("onListenerDisconnected")
        }
    }

    /**
     * Ask the OS to re-establish its binding to our [LibPebbleNotificationListener], but only when
     * the user still grants notification access.
     */
    private fun requestRebindIfNeeded(reason: String) {
        if (!hasNotificationAccess()) {
            logger.d { "Skip listener rebind ($reason): no notification access" }
            return
        }
        logger.w { "Requesting notification listener rebind ($reason)" }
        try {
            NotificationListenerService.requestRebind(
                LibPebbleNotificationListener.componentName(context)
            )
        } catch (e: Exception) {
            logger.e(e) { "requestRebind failed" }
        }
    }

    /**
     * Recovers a binding that is nominally alive (service reference non-null) but no longer
     * delivering.
     */
    private suspend fun recoverStalledBinding(service: LibPebbleNotificationListener) {
        logger.w { "Notification listener bound but not delivering; unbinding then rebinding" }
        try {
            service.requestUnbind()
        } catch (e: Exception) {
            logger.e(e) { "requestUnbind failed; requesting rebind anyway" }
        }
        // Let the unbind land so requestRebind's precondition is satisfied, then drive the rebind
        // directly instead of depending on the onListenerDisconnected callback firing.
        delay(REBIND_DEBOUNCE)
        requestRebindIfNeeded("watchdog: binding not delivering")
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun dismissNotification(itemId: Uuid) {
        val service = listenerService
        if (service == null) {
            logger.w { "Couldn't get service to dismiss notification" }
            return
        }
        service.cancelNotification(itemId)
    }

    fun getChannelsForApp(packageName: String): List<ChannelGroup> {
        val service = listenerService
        if (service == null) {
            logger.w { "Couldn't get service to dismiss notification" }
            return emptyList()
        }
        return service.getChannelsForApp(packageName)
    }

    override fun init(libPebble: LibPebble) {
        notificationHandler.init()
        notificationSendQueue.onEach {
            libPebble.sendNotification(
                it.toTimelineNotification(notificationConfig.value.cannedResponses)
            )
        }.launchIn(libPebbleCoroutineScope)
        notificationDeleteQueue.onEach {
            libPebble.markNotificationRead(it)
        }.launchIn(libPebbleCoroutineScope)

        // Watchdog: catches cases where delivery is lost without an onListenerDisconnected callback
        // (e.g. process restarted but the OS never rebound, or an aggressive OEM ROM silently severs
        // delivery while leaving our service reference in place). If access is granted but we either
        // have no service or the binding no longer delivers, ask the OS to rebind.
        libPebbleCoroutineScope.launch {
            while (true) {
                delay(REBIND_WATCHDOG_INTERVAL)
                val service = listenerService
                when {
                    service == null -> requestRebindIfNeeded("watchdog: no service")
                    !service.isBindingAlive() -> recoverStalledBinding(service)
                }
            }
        }
    }

    companion object {
        private val REBIND_DEBOUNCE = 2.seconds
        private val REBIND_WATCHDOG_INTERVAL = 5.minutes
    }
}