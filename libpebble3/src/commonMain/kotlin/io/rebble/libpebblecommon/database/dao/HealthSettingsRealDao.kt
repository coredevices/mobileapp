package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.ActivityPrefsBlobItem
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.DistanceUnitsBlobItem
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntry
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntrySyncEntity
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue.Companion.encodeToString
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.util.DataBuffer

@Dao
interface HealthSettingsEntryRealDao : HealthSettingsEntryDao {
    @Transaction
    override suspend fun handleWrite(write: DbWrite, transport: String, params: ValueParams): BlobResponse.BlobStatus {
        val key = write.key.toByteArray().decodeToString().trimEnd('\u0000')
        val value = DataBuffer(write.value)

        val entry = try {
            when (key) {
                "activityPreferences" -> {
                    val blob = ActivityPrefsBlobItem(
                        heightMm = 0u, weightDag = 0u,
                        trackingEnabled = false, activityInsightsEnabled = false,
                        sleepInsightsEnabled = false, ageYears = 0, gender = 0,
                    )
                    blob.fromBytes(value)
                    HealthSettingsEntry(
                        id = key,
                        value = ActivityPrefsValue(
                            heightMm = blob.heightMm.get().toShort(),
                            weightDag = blob.weightDag.get().toShort(),
                            trackingEnabled = blob.trackingEnabled.get() != 0.toByte(),
                            activityInsightsEnabled = blob.activityInsightsEnabled.get() != 0.toByte(),
                            sleepInsightsEnabled = blob.sleepInsightsEnabled.get() != 0.toByte(),
                            ageYears = blob.ageYears.get().toInt(),
                            gender = HealthGender.entries.firstOrNull { it.value == blob.gender.get() }
                                ?: HealthGender.Other,
                        ).encodeToString(),
                    )
                }
                "unitsDistance" -> {
                    val blob = DistanceUnitsBlobItem(imperialUnits = false)
                    blob.fromBytes(value)
                    HealthSettingsEntry(
                        id = key,
                        value = UnitsDistanceValue(
                            imperialUnits = blob.imperialUnits.get() != 0.toByte(),
                        ).encodeToString(),
                    )
                }
                else -> {
                    logger.w { "Unknown health settings key from watch: $key" }
                    return BlobResponse.BlobStatus.Success
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to decode health settings blob for key: $key" }
            return BlobResponse.BlobStatus.InvalidData
        }

        logger.d { "Health settings handleWrite: $key -> ${entry.value}" }
        insertOrReplace(entry)
        markSyncedToWatch(
            HealthSettingsEntrySyncEntity(
                recordId = entry.id,
                transport = transport,
                watchSynchHashcode = entry.recordHashCode(),
            )
        )
        return BlobResponse.BlobStatus.Success
    }

    companion object {
        private val logger = Logger.withTag("HealthSettingsRealDao")
    }
}
