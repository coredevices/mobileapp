package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.entity.ActivityPrefsBlobItem
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue
import io.rebble.libpebblecommon.database.entity.ActivityPrefsValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.DistanceUnitsBlobItem
import io.rebble.libpebblecommon.database.entity.HRMonitoringInterval
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntry
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntrySyncEntity
import io.rebble.libpebblecommon.database.entity.HeartRatePreferencesBlobItem
import io.rebble.libpebblecommon.database.entity.HeartRatePreferencesValue
import io.rebble.libpebblecommon.database.entity.HeartRatePreferencesValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.HrmPreferencesBlobItem
import io.rebble.libpebblecommon.database.entity.HrmPreferencesValue
import io.rebble.libpebblecommon.database.entity.HrmPreferencesValue.Companion.encodeToString
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue
import io.rebble.libpebblecommon.database.entity.UnitsDistanceValue.Companion.encodeToString
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.util.DataBuffer
import kotlin.time.Instant

@Dao
interface HealthSettingsEntryRealDao : HealthSettingsEntryDao {
    @Transaction
    override suspend fun handleWrite(write: DbWrite, transport: String, params: ValueParams): BlobResponse.BlobStatus {
        val key = write.key.toByteArray().decodeToString().trimEnd('\u0000')
        val value = DataBuffer(write.value)
        val writeTimestamp = Instant.fromEpochSeconds(write.timestamp.toLong()).asMillisecond()

        val decodedValue = try {
            when (key) {
                "activityPreferences" -> {
                    val blob = ActivityPrefsBlobItem(
                        heightMm = 0u, weightDag = 0u,
                        trackingEnabled = false, activityInsightsEnabled = false,
                        sleepInsightsEnabled = false, ageYears = 0, gender = 0,
                    )
                    blob.fromBytes(value)
                    ActivityPrefsValue(
                        heightMm = blob.heightMm.get().toShort(),
                        weightDag = blob.weightDag.get().toShort(),
                        trackingEnabled = blob.trackingEnabled.get() != 0.toByte(),
                        activityInsightsEnabled = blob.activityInsightsEnabled.get() != 0.toByte(),
                        sleepInsightsEnabled = blob.sleepInsightsEnabled.get() != 0.toByte(),
                        ageYears = blob.ageYears.get().toInt(),
                        gender = HealthGender.entries.firstOrNull { it.value == blob.gender.get() }
                            ?: HealthGender.Other,
                    ).encodeToString()
                }
                "unitsDistance" -> {
                    val blob = DistanceUnitsBlobItem(imperialUnits = false)
                    blob.fromBytes(value)
                    UnitsDistanceValue(
                        imperialUnits = blob.imperialUnits.get() != 0.toByte(),
                    ).encodeToString()
                }
                "hrmPreferences" -> {
                    // SOptional auto-detects presence from remaining buffer length; absent
                    // fields fall back to HrmPreferencesValue defaults below.
                    val blob = HrmPreferencesBlobItem(
                        enabled = false,
                        measurementInterval = 0,
                        hasMeasurementInterval = true,
                        activityTrackingEnabled = false,
                        hasActivityTrackingEnabled = true,
                    )
                    blob.fromBytes(value)
                    val defaults = HrmPreferencesValue()
                    HrmPreferencesValue(
                        enabled = blob.enabled.get() != 0.toByte(),
                        measurementInterval = blob.measurementInterval.get()
                            ?.let { HRMonitoringInterval.fromInt(it) }
                            ?: defaults.measurementInterval,
                        activityTrackingEnabled = blob.activityTrackingEnabled.get()
                            ?: defaults.activityTrackingEnabled,
                    ).encodeToString()
                }
                "heartRatePreferences" -> {
                    val blob = HeartRatePreferencesBlobItem(
                        restingHr = 0u, elevatedHr = 0u, maxHr = 0u,
                        zone1Threshold = 0u, zone2Threshold = 0u, zone3Threshold = 0u,
                    )
                    blob.fromBytes(value)
                    HeartRatePreferencesValue(
                        restingHr = blob.restingHr.get().toShort(),
                        elevatedHr = blob.elevatedHr.get().toShort(),
                        maxHr = blob.maxHr.get().toShort(),
                        zone1Threshold = blob.zone1Threshold.get().toShort(),
                        zone2Threshold = blob.zone2Threshold.get().toShort(),
                        zone3Threshold = blob.zone3Threshold.get().toShort(),
                    ).encodeToString()
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

        val existing = getEntry(key)
        if (existing != null && writeTimestamp.instant <= existing.timestamp.instant) {
            logger.d {
                "Health settings handleWrite stale: $key write=${writeTimestamp.instant} " +
                        "existing=${existing.timestamp.instant}"
            }
            return BlobResponse.BlobStatus.DataStale
        }

        val entry = HealthSettingsEntry(id = key, value = decodedValue, timestamp = writeTimestamp)
        logger.d { "Health settings handleWrite: $key -> ${entry.value} @ ${writeTimestamp.instant}" }
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
