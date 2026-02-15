package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.WatchPrefs
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.database.entity.WatchPrefItem
import io.rebble.libpebblecommon.database.entity.WatchPrefItemDao
import io.rebble.libpebblecommon.database.entity.WatchPrefItemSyncEntity
import io.rebble.libpebblecommon.database.entity.asWatchPrefItem
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Dao
interface WatchPrefRealDao : WatchPrefItemDao {
    @Transaction
    override suspend fun handleWrite(write: DbWrite, transport: String, params: ValueParams): BlobResponse.BlobStatus {
        val writeItem = write.asWatchPrefItem(params)
        if (writeItem == null) {
            logger.e { "Couldn't decode watch pref item from blobdb write: $write" }
            return BlobResponse.BlobStatus.Success
        }
        logger.v { "blobdb handleWrite: $writeItem" }
        val existingItem = getEntry(writeItem.id)
        if (existingItem == null || writeItem.timestamp.instant > existingItem.timestamp.instant) {
            insertOrReplace(writeItem)
            markSyncedToWatch(
                WatchPrefItemSyncEntity(
                    recordId = writeItem.id,
                    transport = transport,
                    watchSynchHashcode = writeItem.recordHashCode(),
                )
            )
            return BlobResponse.BlobStatus.Success
        } else {
            return BlobResponse.BlobStatus.DataStale
        }
    }

    @Query("SELECT * FROM WatchPrefItemEntity")
    fun getAllFlow(): Flow<List<WatchPrefItem>>

    companion object {
        private val logger = Logger.withTag("WatchPrefRealDao")
    }
}

data class WatchPreference<T>(
    val pref: WatchPref<T>,
    // Only populated if non-default has been set (by phone or watch)
    val value: T?,
) {
    fun valueOrDefault(): T = value ?: pref.defaultValue
}

class RealWatchPrefs(
    private val watchPrefRealDao: WatchPrefRealDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val clock: Clock,
) : WatchPrefs {
    private val logger = Logger.withTag("RealWatchPrefs")
    override val watchPrefs: Flow<List<WatchPreference<*>>> = watchPrefRealDao.getAllFlow().map { dbPrefs ->
        val dbValues = dbPrefs.mapNotNull { pref ->
            val prefType = WatchPref.from(pref.id)
            if (prefType == null) {
                logger.w { "Don't know how to encode watch pref key: ${pref.id}" }
                return@mapNotNull null
            }
            prefType.toWatchPreference(pref.value)
        }
        WatchPref.enumeratePrefs().map {
            dbValues.firstOrNull { pref -> pref.pref == it } ?: WatchPreference(it, null)
        }
    }

    override fun setWatchPref(watchPref: WatchPreference<*>) {
        libPebbleCoroutineScope.launch {
            val item = watchPref.watchPrefItem(clock.now().asMillisecond())
            watchPrefRealDao.insertOrReplace(item)
        }
    }
}

private fun <T> WatchPref<T>.toWatchPreference(rawValue: String): WatchPreference<T> {
    return WatchPreference(
        pref = this,
        value = this.decodeValue(rawValue)
    )
}

private fun <T> WatchPreference<T>.watchPrefItem(
    timestamp: MillisecondInstant,
): WatchPrefItem {
    return WatchPrefItem(
        id = pref.id,
        value = pref.encodeValue(valueOrDefault()),
        timestamp = timestamp,
    )
}