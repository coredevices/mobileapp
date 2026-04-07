package coredevices.pebble.health

import co.touchlab.kermit.Logger
import com.viktormykhailiv.kmp.health.HealthDataType
import com.viktormykhailiv.kmp.health.HealthManager
import com.viktormykhailiv.kmp.health.HealthManagerFactory
import com.viktormykhailiv.kmp.health.HealthRecord
import com.viktormykhailiv.kmp.health.records.ExerciseSessionRecord
import com.viktormykhailiv.kmp.health.records.ExerciseType
import com.viktormykhailiv.kmp.health.records.HeartRateRecord
import com.viktormykhailiv.kmp.health.records.SleepSessionRecord
import com.viktormykhailiv.kmp.health.records.SleepStageType
import com.viktormykhailiv.kmp.health.records.StepsRecord
import com.viktormykhailiv.kmp.health.records.metadata.Device
import com.viktormykhailiv.kmp.health.records.metadata.DeviceType
import com.viktormykhailiv.kmp.health.records.metadata.Metadata
import coredevices.util.AppResumed
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlatformHealthSync(
    private val libPebble: LibPebble,
    private val tracker: HealthSyncTracker,
    private val appResumed: AppResumed,
) {
    private val logger = Logger.withTag("PlatformHealthSync")
    private val healthManager: HealthManager = HealthManagerFactory().createManager()
    private val healthDao get() = libPebble.healthDao

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** Start observing health data updates and app foreground events, auto-syncing to the platform. */
    fun startAutoSync(scope: CoroutineScope) {
        scope.launch {
            libPebble.healthDataUpdated.collect {
                sync()
            }
        }
        scope.launch {
            sync()
            appResumed.appResumed.collect {
                sync()
            }
        }
    }

    /** Check if the health platform is available on this device. */
    suspend fun isAvailable(): Boolean {
        return healthManager.isAvailable().getOrDefault(false)
    }

    /** Request write permissions. Returns true if granted. */
    suspend fun requestPermissions(): Boolean {
        val result = healthManager.requestAuthorization(
            readTypes = emptyList(),
            writeTypes = listOf(
                HealthDataType.Steps,
                HealthDataType.HeartRate,
                HealthDataType.Sleep,
                HealthDataType.Exercise(
                    activeEnergyBurned = false,
                    cyclingPower = false,
                    cyclingSpeed = false,
                    flightsClimbed = false,
                    distanceWalkingRunning = true,
                    runningSpeed = false,
                ),
            ),
        )
        return result.getOrDefault(false)
    }

    /** Run a sync: query new data from Room DB, map to HealthKMP records, write. */
    suspend fun sync() {
        if (_syncing.value) return
        if (!tracker.enabled) return

        _syncing.value = true
        try {
            syncStepsAndHeartRate()
            syncOverlays()
            logger.d { "Health platform sync completed" }
        } catch (e: Exception) {
            logger.e(e) { "Health platform sync failed" }
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun syncStepsAndHeartRate() {
        val lastTimestamp = tracker.lastSyncedStepsTimestamp
        val latestTimestamp = healthDao.getLatestTimestamp() ?: return
        if (latestTimestamp <= lastTimestamp) return

        val records = healthDao.getHealthDataAfter(lastTimestamp)
        if (records.isEmpty()) return

        val healthRecords = mutableListOf<HealthRecord>()

        for (entity in records) {
            val startTime = Instant.fromEpochSeconds(entity.timestamp)
            val endTime = startTime + 10.minutes

            // Steps
            if (entity.steps > 0) {
                healthRecords += StepsRecord(
                    startTime = startTime,
                    endTime = endTime,
                    count = entity.steps,
                    metadata = createMetadata(entity.timestamp, "steps"),
                )
            }

            // Heart rate
            if (entity.heartRate in 1..300) {
                healthRecords += HeartRateRecord(
                    startTime = startTime,
                    endTime = endTime,
                    samples = listOf(
                        HeartRateRecord.Sample(
                            time = startTime,
                            beatsPerMinute = entity.heartRate,
                        )
                    ),
                    metadata = createMetadata(entity.timestamp, "hr"),
                )
            }
        }

        if (healthRecords.isNotEmpty()) {
            val result = healthManager.writeData(healthRecords)
            if (result.isSuccess) {
                tracker.lastSyncedStepsTimestamp = records.last().timestamp
                logger.d { "Synced ${healthRecords.size} step/HR records" }
            } else {
                logger.e { "Failed to write step/HR records: ${result.exceptionOrNull()}" }
            }
        } else {
            // All records were zero — still advance the tracker
            tracker.lastSyncedStepsTimestamp = records.last().timestamp
        }
    }

    private suspend fun syncOverlays() {
        val lastTimestamp = tracker.lastSyncedOverlayTimestamp
        val sleepTypes = listOf(
            OverlayType.Sleep.value,
            OverlayType.DeepSleep.value,
            OverlayType.Nap.value,
            OverlayType.DeepNap.value,
        )
        val exerciseTypes = listOf(
            OverlayType.Walk.value,
            OverlayType.Run.value,
            OverlayType.OpenWorkout.value,
        )
        val allTypes = sleepTypes + exerciseTypes

        val overlays = healthDao.getOverlayEntriesAfter(lastTimestamp, allTypes)
        if (overlays.isEmpty()) return

        val sleepOverlays = overlays.filter { it.type in sleepTypes }
        val exerciseOverlays = overlays.filter { it.type in exerciseTypes }

        var allSucceeded = true

        // Write sleep sessions separately so exercise failures don't block sleep
        val sleepRecords = buildSleepSessions(sleepOverlays)
        if (sleepRecords.isNotEmpty()) {
            logger.d { "Writing ${sleepRecords.size} sleep sessions to health platform" }
            val result = healthManager.writeData(sleepRecords)
            if (result.isSuccess) {
                logger.d { "Synced ${sleepRecords.size} sleep records" }
            } else {
                allSucceeded = false
                logger.e { "Failed to write sleep records: ${result.exceptionOrNull()}" }
            }
        }

        // Write exercise records separately
        val exerciseRecords = mutableListOf<HealthRecord>()
        for (overlay in exerciseOverlays) {
            if (overlay.duration <= 0) continue
            val startTime = Instant.fromEpochSeconds(overlay.startTime)
            val endTime = startTime + overlay.duration.seconds

            val overlayType = OverlayType.fromValue(overlay.type) ?: continue
            val exerciseType = when (overlayType) {
                OverlayType.Walk -> ExerciseType.Walking
                OverlayType.Run -> ExerciseType.Running
                OverlayType.OpenWorkout -> ExerciseType.OtherWorkout
                else -> continue
            }

            exerciseRecords += ExerciseSessionRecord(
                startTime = startTime,
                endTime = endTime,
                exerciseType = exerciseType,
                title = when (overlayType) {
                    OverlayType.Walk -> "Walk"
                    OverlayType.Run -> "Run"
                    OverlayType.OpenWorkout -> "Workout"
                    else -> null
                },
                exerciseRoute = null,
                metadata = createMetadata(overlay.startTime, "exercise"),
            )
        }
        if (exerciseRecords.isNotEmpty()) {
            val result = healthManager.writeData(exerciseRecords)
            if (result.isSuccess) {
                logger.d { "Synced ${exerciseRecords.size} exercise records" }
            } else {
                allSucceeded = false
                logger.e { "Failed to write exercise records: ${result.exceptionOrNull()}" }
            }
        }

        // Only advance tracker if all writes succeeded (or there was nothing to write)
        if (allSucceeded) {
            tracker.lastSyncedOverlayTimestamp = overlays.maxOf { it.startTime }
        }
    }

    private fun buildSleepSessions(overlays: List<OverlayDataEntity>): List<SleepSessionRecord> {
        if (overlays.isEmpty()) return emptyList()

        // Filter to only overlays with positive duration before grouping
        val valid = overlays.filter { it.duration > 0 }.sortedBy { it.startTime }
        if (valid.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<OverlayDataEntity>>()
        var currentGroup = mutableListOf(valid.first())

        for (i in 1 until valid.size) {
            val prev = currentGroup.last()
            val prevEnd = prev.startTime + prev.duration
            val curr = valid[i]

            // Group overlays within 2 hours of each other into one session
            if (curr.startTime - prevEnd <= 2 * 3600) {
                currentGroup.add(curr)
            } else {
                groups += currentGroup
                currentGroup = mutableListOf(curr)
            }
        }
        groups += currentGroup

        return groups.mapNotNull { group ->
            try {
                createSleepSession(group)
            } catch (e: Exception) {
                logger.e(e) {
                    "Failed to create sleep session from ${group.size} overlays: " +
                            group.joinToString { "type=${it.type},start=${it.startTime},dur=${it.duration}" }
                }
                null
            }
        }
    }

    private fun createSleepSession(overlays: List<OverlayDataEntity>): SleepSessionRecord {
        val sessionStart = Instant.fromEpochSeconds(overlays.minOf { it.startTime })
        val sessionEnd = Instant.fromEpochSeconds(overlays.maxOf { it.startTime + it.duration })

        // Build non-overlapping stages sorted by start time
        val stages = overlays
            .map { overlay ->
                val stageType = when (OverlayType.fromValue(overlay.type)) {
                    OverlayType.DeepSleep, OverlayType.DeepNap -> SleepStageType.Deep
                    else -> SleepStageType.Light
                }
                SleepSessionRecord.Stage(
                    startTime = Instant.fromEpochSeconds(overlay.startTime),
                    endTime = Instant.fromEpochSeconds(overlay.startTime + overlay.duration),
                    type = stageType,
                )
            }
            .sortedBy { it.startTime }
            .fold(mutableListOf<SleepSessionRecord.Stage>()) { acc, stage ->
                val prev = acc.lastOrNull()
                if (prev != null && stage.startTime < prev.endTime) {
                    // Overlapping stage — trim its start to previous end, or skip if fully contained
                    if (stage.endTime > prev.endTime) {
                        acc += SleepSessionRecord.Stage(
                            startTime = prev.endTime,
                            endTime = stage.endTime,
                            type = stage.type,
                        )
                    }
                    // else: fully contained, skip
                } else {
                    acc += stage
                }
                acc
            }

        logger.d { "Sleep session: ${stages.size} stages, start=$sessionStart, end=$sessionEnd" }

        return SleepSessionRecord(
            startTime = sessionStart,
            endTime = sessionEnd,
            stages = stages,
            metadata = createMetadata(overlays.first().startTime, "sleep"),
        )
    }

    private fun createMetadata(timestamp: Long, prefix: String): Metadata {
        return Metadata.autoRecorded(
            id = "pebble-$prefix-$timestamp",
            device = Device(type = DeviceType.Watch),
        )
    }
}
