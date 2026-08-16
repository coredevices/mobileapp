package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.content.Context
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
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
            if (listenerService == null) forceRebindIfNeeded("onListenerDisconnected")
        }
    }

    /**
     * Make the OS re-establish its binding to our [LibPebbleNotificationListener], but only when the
     * user still grants notification access.
     *
     * Toggling the component is the only recovery that covers a binding lost with the process:
     * [NotificationListenerService.requestRebind] silently does nothing unless we ourselves called
     * [NotificationListenerService.requestUnbind] first. Keep the two calls adjacent — being killed
     * between them leaves the listener disabled until reinstall.
     */
    private fun forceRebindIfNeeded(reason: String) {
        if (!hasNotificationAccess()) {
            logger.d { "Skip listener rebind ($reason): no notification access" }
            return
        }
        logger.w { "Forcing notification listener rebind ($reason)" }
        val component = LibPebbleNotificationListener.componentName(context)
        try {
            context.packageManager.setComponentEnabledSetting(
                component, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP,
            )
            context.packageManager.setComponentEnabledSetting(
                component, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP,
            )
            // A component the OS has snoozed is excluded from the rebind the toggle triggers; this
            // is what un-snoozes it.
            NotificationListenerService.requestRebind(component)
        } catch (e: Exception) {
            logger.e(e) { "Failed to toggle notification listener component" }
        }
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
        notificationHandler.init(activeNotifications = {
            try {
                listenerService?.activeNotifications?.toList()
            } catch (e: Exception) {
                logger.w(e) { "getActiveNotifications failed" }
                null
            }
        })
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
        // have no service or the binding no longer delivers, make the OS rebind.
        libPebbleCoroutineScope.launch {
            while (true) {
                delay(REBIND_WATCHDOG_INTERVAL)
                val service = listenerService
                when {
                    service == null -> forceRebindIfNeeded("watchdog: no service")
                    !service.isBindingAlive() -> forceRebindIfNeeded("watchdog: binding not delivering")
                }
            }
        }
    }

    companion object {
        private val REBIND_DEBOUNCE = 2.seconds
        private val REBIND_WATCHDOG_INTERVAL = 5.minutes
    }
}