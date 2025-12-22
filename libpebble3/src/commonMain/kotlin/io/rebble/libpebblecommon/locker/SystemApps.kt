package io.rebble.libpebblecommon.locker

import io.rebble.libpebblecommon.SystemAppIDs
import io.rebble.libpebblecommon.metadata.WatchType
import kotlin.uuid.Uuid

enum class SystemApps(
    val uuid: Uuid,
    val displayName: String,
    val type: AppType,
    val compatiblePlatforms: List<WatchType>,
    val defaultOrder: Int,
) {
    Settings(SystemAppIDs.SETTINGS_APP_UUID, "Settings", AppType.Watchapp, WatchType.entries, defaultOrder = -10),
    Music(SystemAppIDs.MUSIC_APP_UUID, "Music", AppType.Watchapp, WatchType.entries, defaultOrder = -9),
    Notifications(SystemAppIDs.NOTIFICATIONS_APP_UUID, "Notifications", AppType.Watchapp, WatchType.entries, defaultOrder = -8),
    Alarms(SystemAppIDs.ALARMS_APP_UUID, "Alarms", AppType.Watchapp, WatchType.entries, defaultOrder = -7),
    Workout(SystemAppIDs.WORKOUT_APP_UUID, "Workout", AppType.Watchapp, WatchType.entries, defaultOrder = -6),
    Watchfaces(SystemAppIDs.WATCHFACES_APP_UUID, "Watchfaces", AppType.Watchapp, WatchType.entries, defaultOrder = -5),
}
