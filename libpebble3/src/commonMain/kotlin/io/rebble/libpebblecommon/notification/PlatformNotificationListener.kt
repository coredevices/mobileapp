package io.rebble.libpebblecommon.notification

import androidx.compose.ui.graphics.ImageBitmap
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.connection.Vibrations
import io.rebble.libpebblecommon.database.dao.AppWithCount
import io.rebble.libpebblecommon.database.dao.ChannelAndCount
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.dao.NotificationDao
import io.rebble.libpebblecommon.database.dao.VibePatternDao
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationEntity
import io.rebble.libpebblecommon.database.entity.VibePatternEntity
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface NotificationListenerConnection {
    fun init(libPebble: LibPebble)
}

interface NotificationAppsSync {
    fun init()
}

data class VibePattern(
    val name: String,
    val pattern: List<Long>,
    val bundled: Boolean,
) {
    companion object {
        fun VibePattern.uIntPattern() = pattern.map { it.toUInt() }
    }
}

class NotificationApi(
    private val notificationAppsSync: NotificationAppsSync,
    private val notificationAppDao: NotificationAppRealDao,
    private val notificationsDao: NotificationDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val appContext: AppContext,
    private val vibePatternDao: VibePatternDao,
) : NotificationApps, Vibrations {
    fun init() {
        notificationAppsSync.init()
    }

    override fun notificationApps(): Flow<List<AppWithCount>> =
        notificationAppDao.allAppsWithCountsFlow()

    override fun notificationAppChannelCounts(packageName: String): Flow<List<ChannelAndCount>> =
        notificationsDao.channelNotificationCounts(packageName)

    override fun mostRecentNotificationsFor(
        pkg: String?,
        channelId: String?,
        contactId: String?,
        limit: Int,
    ): Flow<List<NotificationEntity>> = notificationsDao.mostRecentNotificationsFor(
        pkg = pkg,
        channelId = channelId,
        contactId = contactId,
        limit = limit,
    )

    override fun updateNotificationAppMuteState(packageName: String?, muteState: MuteState) {
        libPebbleCoroutineScope.launch {
            if (packageName != null) {
                notificationAppDao.updateAppMuteState(packageName, muteState)
            } else {
                notificationAppDao.updateAllAppMuteStates(muteState)
            }
        }
    }

    override fun updateNotificationAppState(
        packageName: String,
        vibePatternName: String?,
        colorName: String?,
        iconCode: String?,
    ) {
        libPebbleCoroutineScope.launch {
            notificationAppDao.updateAppState(
                packageName = packageName,
                vibePatternName = vibePatternName,
                colorName = colorName,
                iconCode = iconCode,
            )
        }
    }

    override fun updateNotificationChannelMuteState(
        packageName: String,
        channelId: String,
        muteState: MuteState,
    ) {
        libPebbleCoroutineScope.launch {
            val appEntry = notificationAppDao.getEntry(packageName) ?: return@launch
            notificationAppDao.insertOrReplace(appEntry.copy(channelGroups = appEntry.channelGroups.map { g ->
                g.copy(channels = g.channels.map { c ->
                    if (c.id == channelId) {
                        c.copy(muteState = muteState)
                    } else {
                        c
                    }
                })
            }))
        }
    }

    override suspend fun getAppIcon(packageName: String): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            iconFor(packageName, appContext)
        }
    }

    override fun vibePatterns(): Flow<List<VibePattern>> = vibePatternDao.getVibePatterns().map { patterns ->
        patterns.map { pattern ->
            VibePattern(
                name = pattern.name,
                pattern = pattern.pattern.map { it.toLong() },
                bundled = pattern.bundled,
            )
        }
    }

    override fun addCustomVibePattern(
        name: String,
        pattern: List<Long>,
    ) {
        libPebbleCoroutineScope.launch {
            vibePatternDao.insertOrIgnore(
                VibePatternEntity(
                    name = name,
                    pattern = pattern.map { it.toUInt() },
                    bundled = false,
                )
            )
        }
    }

    override fun deleteCustomPattern(name: String) {
        libPebbleCoroutineScope.launch {
            vibePatternDao.deleteCustomPattern(name)
        }
    }
}

expect fun iconFor(packageName: String, appContext: AppContext): ImageBitmap?
