package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.database.entity.NotificationAppItemDao
import io.rebble.libpebblecommon.database.entity.NotificationAppItemSyncEntity
import io.rebble.libpebblecommon.database.entity.asNotificationAppItem
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

data class AppWithCount(
    @Embedded val app: NotificationAppItem,
    val count: Int,
)

@Dao
interface NotificationAppRealDao : NotificationAppItemDao {
    @Query("SELECT * FROM NotificationAppItemEntity WHERE deleted = 0 ORDER BY name ASC")
    suspend fun allApps(): List<NotificationAppItem>

    @Query("SELECT * FROM NotificationAppItemEntity WHERE deleted = 0 ORDER BY name ASC")
    fun allAppsFlow(): Flow<List<NotificationAppItem>>

    @Query("SELECT a.*, COUNT(ne.id) as count " +
            "FROM NotificationAppItemEntity a " +
            "LEFT JOIN NotificationEntity ne ON a.packageName = ne.pkg " +
            "WHERE a.deleted = 0 " +
            "GROUP BY a.packageName " +
            "ORDER BY a.name ASC")
    fun allAppsWithCountsFlow(): Flow<List<AppWithCount>>

    @Transaction
    suspend fun updateAppState(
        packageName: String,
        vibePatternName: String?,
        colorName: String?,
        iconCode: String?,
    ) {
        val existing = getEntry(packageName)
        if (existing == null) {
            logger.e("updateAppState: no record to update!")
            return
        }
        insertOrReplace(existing.copy(
            vibePatternName = vibePatternName,
            colorName = colorName,
            iconCode = iconCode,
//            stateUpdated = Clock.System.now().asMillisecond(),
        ))
    }

    @Transaction
    suspend fun updateAppMuteState(packageName: String, muteState: MuteState) {
        val existing = getEntry(packageName)
        if (existing == null) {
            logger.e("updateAppMuteState: no record to update!")
            return
        }
        insertOrReplace(existing.copy(
            muteState = muteState,
            stateUpdated = Clock.System.now().asMillisecond(),
            muteExpiration = null,
        ))
    }

    @Transaction
    suspend fun updateAllAppMuteStates(muteState: MuteState) {
        insertOrReplace(allApps().map {
            it.copy(
                muteState = muteState,
                stateUpdated = Clock.System.now().asMillisecond(),
            )
        })
    }

    @Transaction
    override suspend fun handleWrite(write: DbWrite, transport: String): BlobResponse.BlobStatus {
        val writeItem = write.asNotificationAppItem()
        if (writeItem == null) {
            logger.e { "Couldn't decode app notification item from write: $write" }
            return BlobResponse.BlobStatus.InvalidData
        }
        val existingItem = getEntry(writeItem.packageName)
        logger.d { "existingItem=$existingItem writeItem=$writeItem" }
        if (existingItem == null || writeItem.stateUpdated.instant > existingItem.stateUpdated.instant) {
            insertOrReplace(writeItem)
            markSyncedToWatch(
                NotificationAppItemSyncEntity(
                    recordId = writeItem.packageName,
                    transport = transport,
                    watchSynchHashcode = writeItem.recordHashCode(),
                )
            )
        }
        return BlobResponse.BlobStatus.Success
    }

    companion object {
        private val logger = Logger.withTag("NotificationAppRealDao")
    }
}
