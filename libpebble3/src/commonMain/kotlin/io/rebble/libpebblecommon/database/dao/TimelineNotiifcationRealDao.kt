package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.TimelineNotificationDao
import io.rebble.libpebblecommon.packets.blobdb.TimelineItemFlag
import kotlin.uuid.Uuid

@Dao
internal interface TimelineNotificationRealDao : TimelineNotificationDao {
    suspend fun markNotificationRead(itemId: Uuid) {
        val existingNotification = getEntry(itemId)
        if (existingNotification == null) {
            Logger.w { "Couldn't find notification $itemId to mark read" }
            return
        }
        insertOrReplace(
            existingNotification.copy(
                content = existingNotification.content.copy(
                    flags = existingNotification.content.flags.plus(TimelineItemFlag.STATE_READ)
                )
            )
        )
    }
}