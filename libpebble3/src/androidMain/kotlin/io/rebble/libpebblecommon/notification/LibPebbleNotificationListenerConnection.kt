package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.notification.LibPebbleNotificationListener
import io.rebble.libpebblecommon.notification.NotificationListenerConnection
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.uuid.Uuid

class AndroidPebbleNotificationListenerConnection(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val notificationHandler: NotificationHandler,
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
            libPebble.sendNotification(it.toTimelineNotification())
        }.launchIn(libPebbleCoroutineScope)
        notificationDeleteQueue.onEach {
            libPebble.markNotificationRead(it)
        }.launchIn(libPebbleCoroutineScope)
    }
}