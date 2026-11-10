package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import io.rebble.libpebblecommon.connection.DisplayedNotificationReplyResult
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationReplies
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.notification.NotificationAppsSync
import io.rebble.libpebblecommon.notification.NotificationListenerConnection
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import platform.Foundation.NSBundle
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IosNotificationActionHandler : PlatformNotificationActionHandler, NotificationReplies {
    override suspend operator fun invoke(
        itemId: Uuid,
        action: BaseAction,
        attributes: List<TimelineItem.Attribute>
    ): TimelineActionResult {
        error("Notification actions are not handled by app on iOS")
    }

    override suspend fun replyToDisplayedNotification(text: String, at: Instant) =
        DisplayedNotificationReplyResult.Unsupported
}

class IosNotificationListenerConnection : NotificationListenerConnection {
    override fun init(libPebble: LibPebble) {
    }
}

/**
 * The app sends its own notifications to the watch directly, so its ANCS copies are muted there.
 */
class IosNotificationAppsSync(
    private val notificationAppDao: NotificationAppRealDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
) : NotificationAppsSync {
    private val ownBundleId = NSBundle.mainBundle.bundleIdentifier

    override val hiddenPackages: Set<String> = setOfNotNull(ownBundleId)

    override fun init() {
        val bundleId = ownBundleId ?: return
        notificationAppDao.allAppsFlow()
            .map { apps -> apps.firstOrNull { it.packageName == bundleId } }
            .filter { it?.muteState != MuteState.Always }
            .onEach { existing ->
                val now = Clock.System.now().asMillisecond()
                notificationAppDao.insertOrReplace(
                    existing?.copy(muteState = MuteState.Always, stateUpdated = now, muteExpiration = null)
                        ?: NotificationAppItem(
                            packageName = bundleId,
                            name = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String ?: bundleId,
                            muteState = MuteState.Always,
                            channelGroups = emptyList(),
                            stateUpdated = now,
                            lastNotified = now,
                            vibePatternName = null,
                            colorName = null,
                            iconCode = null,
                        )
                )
            }
            .launchIn(libPebbleCoroutineScope)
    }
}
