package coredevices.util

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

class IosWatchNotifier(
    private val libPebble: LibPebble,
) : WatchNotifier {
    private val logger = Logger.withTag("IosWatchNotifier")
    private val appDisplayName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String ?: "Pebble"

    /** The watch ignores a re-sent notification ID, so each send gets a new one; this maps keys to the latest. */
    private val currentItemIds = mutableMapOf<String, Uuid>()

    /** Runs operations in order, one at a time, so they don't race on [currentItemIds] or the watch. */
    private val ops: Channel<suspend () -> Unit> by lazy {
        Channel<suspend () -> Unit>(Channel.UNLIMITED).also { channel ->
            GlobalScope.launch {
                for (op in channel) {
                    try {
                        op()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e(e) { "Watch notification operation failed" }
                    }
                }
            }
        }
    }

    override fun send(key: String, title: String, body: String?, actions: List<WatchNotificationAction>) {
        val titleText = title
        val bodyText = body
        val watchActions = actions
        val notification = buildTimelineNotification(parentId = Uuid.NIL, timestamp = Clock.System.now()) {
            layout = TimelineItem.Layout.GenericNotification
            attributes {
                appName { appDisplayName }
                tinyIcon { TimelineIcon.NotificationGeneric }
                title { titleText }
                bodyText?.let { body { it } }
            }
            actions {
                watchActions.forEach { action ->
                    action(TimelineItem.Action.Type.Generic, internalType = action.internalType) {
                        attributes { title { action.title } }
                        internalData = action.data
                    }
                }
            }
        }
        ops.trySend {
            currentItemIds.put(key, notification.itemId)?.let { libPebble.markNotificationRead(it) }
            libPebble.sendNotification(notification)
        }
    }

    override fun remove(key: String) {
        ops.trySend {
            currentItemIds.remove(key)?.let { libPebble.markNotificationRead(it) }
        }
    }
}
