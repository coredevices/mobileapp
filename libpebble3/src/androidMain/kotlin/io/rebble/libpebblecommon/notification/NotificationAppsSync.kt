package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.notification.NotificationAppsSync
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.obfuscate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

class AndroidNotificationAppsSync(
    private val context: AppContext,
    private val notificationAppDao: NotificationAppRealDao,
    private val timeProvider: TimeProvider,
    private val notificationListenerConnection: AndroidPebbleNotificationListenerConnection,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val androidPackageChangedReceiver: AndroidPackageChangedReceiver,
    private val notificationHandler: NotificationHandler,
    private val privateLogger: PrivateLogger,
    private val notificationConfigFlow: NotificationConfigFlow,
) : NotificationAppsSync {
    private val logger = Logger.withTag("NotificationAppsSync")
    private val syncTrigger = MutableSharedFlow<Unit>()

    override fun init() {
        libPebbleCoroutineScope.launch {
            libPebbleCoroutineScope.launch {
                syncTrigger.conflate().collect {
                    syncAppsFromOS()
                }
            }
            // Make sure the above is collecting already
            delay(1.seconds)
            requestSync()
            androidPackageChangedReceiver.registerForPackageChanges {
                libPebbleCoroutineScope.launch {
                    // TODO be capable of only processing changes for this package, rather than an
                    //  entire resync of all apps
                    requestSync()
                }
            }
            libPebbleCoroutineScope.launch {
                // This one can be spammy - debounce
                notificationHandler.channelChanged.conflate().sample(5.seconds).collect {
                    logger.d { "Channel changed" }
                    requestSync()
                }
            }
            libPebbleCoroutineScope.launch {
                // This one can be spammy - debounce
                notificationHandler.notificationServiceBound.collect {
                    logger.d { "Notification access granted" }
                    // This means we have notification channels access (we probably previously had a
                    // list of installed apps but not with channels).
                    requestSync()
                }
            }
        }
    }

    private suspend fun requestSync() {
        syncTrigger.emit(Unit)
    }

    private suspend fun syncAppsFromOS() = withContext(Dispatchers.IO) {
        logger.d("syncAppsFromOS")
        val pm = context.context.packageManager
        val existingApps =
            notificationAppDao.allApps().associateBy { it.packageName }.toMutableMap()
        val osApps = pm.getInstalledApplications(0)
        val notificationConfig = notificationConfigFlow.value
        osApps.onEach { osApp ->
            // null = this is a system app
            pm.getLaunchIntentForPackage(osApp.packageName) ?: return@onEach
            val existing = existingApps.remove(osApp.packageName)
            val channels = notificationListenerConnection.getChannelsForApp(osApp.packageName)
            val name = pm.getApplicationLabel(osApp).toString()
            val newAppItem = NotificationAppItem(
                packageName = osApp.packageName,
                name = name,
                muteState = notificationConfig.defaultMuteStateForPackage(osApp.packageName),
                channelGroups = channels,
                stateUpdated = timeProvider.now().asMillisecond(),
                lastNotified = Instant.DISTANT_PAST.asMillisecond(),
                vibePatternName = null,
                colorName = null,
                iconCode = null,
            )
            if (existing == null) {
//                logger.d("adding ${osApp.packageName}")
                notificationAppDao.insertOrReplace(newAppItem)
            } else {
                val newEntryWithExistingStates = newAppItem.copy(
                    muteState = existing.muteState,
                    channelGroups = newAppItem.channelGroups.map { group ->
                        group.copy(
                            channels =
                                group.channels.map { ch ->
                                    ch.copy(
                                        muteState = existing.findChannel(
                                            group.id,
                                            ch.id
                                        )?.muteState ?: MuteState.Never
                                    )
                                })
                    },
                    stateUpdated = existing.stateUpdated,
                    lastNotified = existing.lastNotified,
                    vibePatternName = existing.vibePatternName,
                    colorName = existing.colorName,
                    iconCode = existing.iconCode,
                )
                if (existing != newEntryWithExistingStates) {
                    logger.d("updating ${osApp.packageName.obfuscate(privateLogger)}")
                    notificationAppDao.insertOrReplace(
                        newEntryWithExistingStates.copy(
                            stateUpdated = timeProvider.now().asMillisecond()
                        )
                    )
                }
            }
        }
        existingApps.values.forEach { app ->
            logger.d("deleting $app")
            notificationAppDao.markForDeletion(app.packageName)
        }
        logger.d("/syncAppsFromOS")
    }

    companion object {
        private fun NotificationConfig.defaultMuteStateForPackage(pkg: String) = when {
            !defaultAppsToEnabled -> MuteState.Always
            pkg in NOTIFICATIONS_DISABLED_BY_DEFAULT_PACKAGES -> MuteState.Always
            else -> MuteState.Never
        }

        private val NOTIFICATIONS_DISABLED_BY_DEFAULT_PACKAGES = setOf(
            "com.samsung.android.calendar",
            "com.google.android.calendar",
        )
    }
}

private fun NotificationAppItem.findChannel(groupId: String, channelId: String): ChannelItem? {
    return channelGroups.find { it.id == groupId }
        ?.channels?.find { it.id == channelId }
}

class AndroidPackageChangedReceiver(private val context: Application) {
    private val logger = Logger.withTag("NotificationAppsSync")

    fun registerForPackageChanges(onChanged: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger.d { "Package changed: ${intent.action}" }
                onChanged()
            }
        }
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")
        context.registerReceiver(receiver, filter)
    }
}
