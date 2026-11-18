package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.notification.NotificationAppsSync
import io.rebble.libpebblecommon.notification.processor.NotificationProperties
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.obfuscate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    private val settings: Settings,
) : NotificationAppsSync {
    private val logger = Logger.withTag("NotificationAppsSync")
    private val syncRequests = Channel<SyncRequest>(Channel.UNLIMITED)

    private sealed interface SyncRequest {
        data object All : SyncRequest
        data class Package(val packageName: String) : SyncRequest
    }

    override fun init() {
        libPebbleCoroutineScope.launch {
            libPebbleCoroutineScope.launch {
                notificationHandler.notificationServiceBound.collect {
                    logger.d { "Notification access granted" }
                    // This means we have notification channels access (we probably previously had a
                    // list of installed apps but not with channels).
                    syncRequests.trySend(SyncRequest.All)
                }
            }
            // Channels are only readable once the listener is bound, so wait for it before the first sync.
            withTimeoutOrNull(LISTENER_BIND_WAIT) {
                notificationHandler.notificationServiceBound
                    .onSubscription { if (notificationListenerConnection.getService() != null) emit(Unit) }
                    .first()
            }
            syncRequests.trySend(SyncRequest.All)
            libPebbleCoroutineScope.launch { processSyncRequests() }
            androidPackageChangedReceiver.registerForPackageChanges(
                onPackageChanged = { syncRequests.trySend(SyncRequest.Package(it)) },
                onLocaleChanged = { syncRequests.trySend(SyncRequest.All) },
            )
            libPebbleCoroutineScope.launch {
                notificationHandler.channelChanged.collect {
                    syncRequests.trySend(SyncRequest.Package(it))
                }
            }
        }
    }

    private suspend fun processSyncRequests() {
        for (first in syncRequests) {
            // Let bursts (e.g. an app recreating all its channels) collapse into one sync.
            delay(SYNC_DEBOUNCE)
            val batch = mutableListOf(first)
            while (true) batch += syncRequests.tryReceive().getOrNull() ?: break
            try {
                if (SyncRequest.All in batch) {
                    syncAppsFromOS()
                } else {
                    batch.filterIsInstance<SyncRequest.Package>()
                        .map { it.packageName }
                        .toSet()
                        .forEach { syncPackage(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e) { "App sync failed" }
            }
        }
    }

    private suspend fun syncAppsFromOS() = withContext(Dispatchers.IO) {
        logger.d("syncAppsFromOS")
        val syncStart = timeProvider.now()
        val labelState = labelState()
        val pm = context.context.packageManager
        val existingApps =
            notificationAppDao.allApps().associateBy { it.packageName }.toMutableMap()
        pm.getInstalledPackages(0).forEach { osPackage ->
            syncApp(pm, osPackage, existingApps.remove(osPackage.packageName), labelState)
        }
        existingApps.values
            .filter { !it.autoAdded }
            .forEach { app ->
                logger.d("deleting $app")
                notificationAppDao.markForDeletion(app.packageName)
            }
        settings[KEY_LAST_FULL_SYNC] = syncStart.toEpochMilliseconds()
        settings[KEY_LABEL_LOCALES] = labelState.locales
        logger.d("/syncAppsFromOS")
    }

    private suspend fun syncPackage(packageName: String) = withContext(Dispatchers.IO) {
        val pm = context.context.packageManager
        val existing = notificationAppDao.getEntry(packageName)
        val osPackage = try {
            pm.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        if (osPackage == null) {
            if (existing != null && !existing.autoAdded) {
                logger.d("deleting ${packageName.obfuscate(privateLogger)}")
                notificationAppDao.markForDeletion(packageName)
            }
            return@withContext
        }
        syncApp(pm, osPackage, existing, labelState())
    }

    private suspend fun syncApp(
        pm: PackageManager,
        osPackage: PackageInfo,
        existing: NotificationAppItem?,
        labelState: LabelState,
    ) {
        val osApp = osPackage.applicationInfo ?: return
        val isSystemApp = (osApp.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            NotificationProperties.lookup(osApp.packageName) == null
        val channels = notificationListenerConnection.getChannelsForApp(osApp.packageName)
        // Loading a label loads the app's resources, so reuse the stored one unless it may have changed.
        val name = if (existing != null && !needsLabelReload(existing, osPackage.lastUpdateTime, labelState)) {
            existing.name
        } else {
            pm.getApplicationLabel(osApp).toString()
        }
        if (existing == null) {
            notificationAppDao.insertOrReplace(
                NotificationAppItem(
                    packageName = osApp.packageName,
                    name = name,
                    muteState = notificationConfigFlow.value.defaultMuteStateForPackage(osApp.packageName, isSystemApp),
                    channelGroups = channels,
                    stateUpdated = timeProvider.now().asMillisecond(),
                    lastNotified = Instant.DISTANT_PAST.asMillisecond(),
                    vibePatternName = null,
                    colorName = null,
                    iconCode = null,
                    allowDuplicates = NotificationProperties.lookup(osApp.packageName)?.allowDuplicates ?: false,
                    isSystemApp = isSystemApp,
                    autoAdded = false,
                )
            )
        } else {
            val newEntryWithExistingStates = existing.mergedWithOsApp(
                name = name,
                channels = channels,
                isSystemApp = isSystemApp,
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

    private fun labelState(): LabelState {
        val locales = context.context.resources.configuration.locales.toLanguageTags()
        return LabelState(
            locales = locales,
            lastFullSyncMs = settings.getLongOrNull(KEY_LAST_FULL_SYNC),
            localesChanged = settings.getStringOrNull(KEY_LABEL_LOCALES) != locales,
        )
    }

    companion object {
        private val LISTENER_BIND_WAIT = 10.seconds
        private val SYNC_DEBOUNCE = 2.seconds
        private const val KEY_LAST_FULL_SYNC = "notification_apps_last_full_sync"
        private const val KEY_LABEL_LOCALES = "notification_apps_label_locales"

        internal fun NotificationConfig.defaultMuteStateForPackage(
            pkg: String,
            isSystemApp: Boolean,
        ) = when {
            !defaultAppsToEnabled -> MuteState.Always
            pkg in NOTIFICATIONS_DISABLED_BY_DEFAULT_PACKAGES -> MuteState.Always
            isSystemApp -> MuteState.Always
            else -> MuteState.Never
        }

        private val NOTIFICATIONS_DISABLED_BY_DEFAULT_PACKAGES = setOf(
            "com.samsung.android.calendar",
            "com.google.android.calendar",
        )
    }
}

internal data class LabelState(
    val locales: String,
    val lastFullSyncMs: Long?,
    val localesChanged: Boolean,
)

internal fun needsLabelReload(existing: NotificationAppItem, lastUpdateTime: Long, state: LabelState): Boolean =
    existing.name.isBlank() ||
        existing.autoAdded ||
        state.localesChanged ||
        state.lastFullSyncMs == null ||
        lastUpdateTime >= state.lastFullSyncMs

// Merge onto the existing row rather than onto a freshly built one: only the name, channels and
// system flag come from the OS, so every user-set field survives a resync even if it is added to
// NotificationAppItem later.
internal fun NotificationAppItem.mergedWithOsApp(
    name: String,
    channels: List<ChannelGroup>,
    isSystemApp: Boolean,
): NotificationAppItem = copy(
    name = name,
    // An empty list from the OS doesn't mean the app has no channels: getChannelsForApp() also
    // returns empty whenever the notification listener isn't bound, which is the normal state for
    // a while after a reboot or an Android user switch. Overwriting would wipe channel mutes.
    channelGroups = if (channels.isEmpty()) channelGroups else channels.map { group ->
        group.copy(
            channels = group.channels.map { channel ->
                channel.copy(muteState = carriedOverMuteState(group, channel))
            },
        )
    },
    isSystemApp = isSystemApp,
    autoAdded = false,
)

// Some apps (e.g. Snapchat, Signal) recreate channels with new IDs; fall back to
// group+channel name so a muted channel stays muted.
internal fun NotificationAppItem.carriedOverMuteState(
    group: ChannelGroup,
    channel: ChannelItem,
): MuteState = (findChannel(group.id, channel.id)
    ?: findChannelByName(group.name, channel.name))?.muteState
    ?: MuteState.Never

private fun NotificationAppItem.findChannel(groupId: String, channelId: String): ChannelItem? {
    return channelGroups.find { it.id == groupId }
        ?.channels?.find { it.id == channelId }
}

private fun NotificationAppItem.findChannelByName(groupName: String?, channelName: String): ChannelItem? {
    return channelGroups.find { it.name == groupName }
        ?.channels?.find { it.name == channelName }
}

class AndroidPackageChangedReceiver(private val context: Application) {
    private val logger = Logger.withTag("NotificationAppsSync")

    fun registerForPackageChanges(onPackageChanged: (String) -> Unit, onLocaleChanged: () -> Unit) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger.d { "Package changed: ${intent.action}" }
                // An update sends REMOVED then ADDED; syncing in between would delete the app's settings.
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                    intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                ) return
                intent.data?.schemeSpecificPart?.let(onPackageChanged)
            }
        }
        val packageFilter = IntentFilter()
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addDataScheme("package")
        context.registerReceiver(packageReceiver, packageFilter)

        val localeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger.d { "Locale changed" }
                onLocaleChanged()
            }
        }
        context.registerReceiver(localeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
    }
}
