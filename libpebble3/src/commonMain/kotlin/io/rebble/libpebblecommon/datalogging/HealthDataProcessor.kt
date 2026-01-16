package io.rebble.libpebblecommon.datalogging

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.insertHealthDataWithPriority
import io.rebble.libpebblecommon.database.dao.insertOverlayDataWithDeduplication
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.health.parsers.parseOverlayData
import io.rebble.libpebblecommon.health.parsers.parseStepsData
import io.rebble.libpebblecommon.services.updateHealthStatsInDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock.System
import kotlin.uuid.Uuid

/**
 * Processes health data from DataLogging sessions.
 *
 * This class is called by Datalogging when health tags (81-85) are received,
 * and handles session tracking, data parsing, and database storage.
 *
 * All received health data is processed immediately to prevent data loss.
 * Battery optimization is handled by throttling sync REQUESTS, not data reception.
 */
class HealthDataProcessor(
    private val scope: LibPebbleCoroutineScope,
    private val healthDao: HealthDao,
    private val healthStatDao: HealthStatDao,
) {
    private val logger = Logger.withTag("HealthDataProcessor")

    private val healthSessions = mutableMapOf<UByte, HealthSession>()
    private val pendingData = mutableMapOf<UByte, MutableList<PendingDataItem>>()
    private val lastTodayUpdateDate = MutableStateFlow<LocalDate?>(null)
    private val lastTodayUpdateTime = MutableStateFlow(0L)
    private val _healthDataUpdated = MutableSharedFlow<Unit>(replay = 0)

    val healthDataUpdated: SharedFlow<Unit> = _healthDataUpdated

    companion object {
        private const val HEALTH_STEPS_TAG: UInt = 81u
        private const val HEALTH_SLEEP_TAG: UInt = 83u
        private const val HEALTH_OVERLAY_TAG: UInt = 84u
        private const val HEALTH_HR_TAG: UInt = 85u

        val HEALTH_TAGS = setOf(HEALTH_STEPS_TAG, HEALTH_SLEEP_TAG, HEALTH_OVERLAY_TAG, HEALTH_HR_TAG)
    }

    fun handleSessionOpen(sessionId: UByte, tag: UInt, applicationUuid: Uuid, itemSize: UShort) {
        if (tag !in HEALTH_TAGS) return

        val session = HealthSession(tag, applicationUuid, itemSize)
        healthSessions[sessionId] = session
        logger.d {
            "HEALTH_SESSION: Opened session $sessionId for ${tagName(tag)} (tag=$tag, itemSize=$itemSize bytes)"
        }

        // Process any data that arrived before the session was opened
        pendingData.remove(sessionId)?.let { pending ->
            logger.d { "HEALTH_SESSION: Processing ${pending.size} pending data items for session $sessionId" }
            pending.forEach { item ->
                processDataItem(session, sessionId, item.payload, item.itemsLeft)
            }
        }
    }

    fun handleSendDataItems(sessionId: UByte, payload: ByteArray, itemsLeft: UInt) {
        val session = healthSessions[sessionId]
        if (session == null) {
            // Queue data for processing when session opens (handles race condition)
            logger.w { "HEALTH_DATA: Session $sessionId not found, queuing ${payload.size} bytes for later processing" }
            pendingData.getOrPut(sessionId) { mutableListOf() }
                .add(PendingDataItem(payload, itemsLeft))
            return
        }

        processDataItem(session, sessionId, payload, itemsLeft)
    }

    private fun processDataItem(session: HealthSession, sessionId: UByte, payload: ByteArray, itemsLeft: UInt) {
        val payloadSize = payload.size
        logger.d { "HEALTH_DATA: handleSendDataItems called (session=$sessionId, ${payloadSize} bytes, $itemsLeft items remaining)" }

        // Process and store the health data in the database
        scope.launch {
            try {
                logger.d { "HEALTH_DATA: Inside coroutine, about to process health data for session $sessionId" }
                val summary = processHealthData(session, payload)

                logger.d {
                    buildString {
                        append("HEALTH_SESSION: Received data for ${tagName(session.tag)} (session=$sessionId, ")
                        append("$payloadSize bytes, $itemsLeft items remaining")
                        if (summary != null) {
                            append(") - $summary")
                        } else {
                            append(")")
                        }
                    }
                }

                // Update today's movement and recent sleep data when we finish receiving a batch
                if (itemsLeft.toInt() == 0) {
                    val timeZone = TimeZone.currentSystemDefault()
                    val today = System.now().toLocalDateTime(timeZone).date
                    val now = System.now().toEpochMilliseconds()
                    val timeSinceLastUpdate = now - lastTodayUpdateTime.value

                    val shouldUpdate = lastTodayUpdateDate.value != today

                    if (shouldUpdate) {
                        logger.d {
                            "HEALTH_DATA: Received new data, updating today's movement and recent sleep data (last update ${timeSinceLastUpdate / 60_000}min ago)"
                        }
                        // Today's data is included in the weekly update, no separate call needed
                        updateHealthStatsInDatabase(healthDao, healthStatDao, today, today.minus(DatePeriod(days = 29)), timeZone)
                        lastTodayUpdateDate.value = today
                        lastTodayUpdateTime.value = now
                    } else {
                        logger.d { "HEALTH_DATA: Skipping today update - already updated today" }
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "HEALTH_DATA: Failed to process health data for session $sessionId (${payload.size} bytes)" }
            }
        }
    }

    fun handleSessionClose(sessionId: UByte) {
        healthSessions.remove(sessionId)?.let { session ->
            logger.d { "HEALTH_SESSION: Closed session $sessionId for ${tagName(session.tag)}" }
        }
        // Clean up any pending data that was never processed
        pendingData.remove(sessionId)?.let { pending ->
            if (pending.isNotEmpty()) {
                logger.w { "HEALTH_SESSION: Discarding ${pending.size} pending data items for closed session $sessionId (session opened too late or never)" }
            }
        }
    }

    private fun tagName(tag: UInt): String =
        when (tag) {
            HEALTH_STEPS_TAG -> "STEPS"
            HEALTH_SLEEP_TAG -> "SLEEP"
            HEALTH_OVERLAY_TAG -> "OVERLAY"
            HEALTH_HR_TAG -> "HEART_RATE"
            else -> "UNKNOWN($tag)"
        }

    private suspend fun processHealthData(session: HealthSession, payload: ByteArray): String? {
        // Only process health data from the system app
        if (session.appUuid != SYSTEM_APP_UUID) {
            logger.d { "Ignoring health data from non-system app: ${session.appUuid}" }
            return null
        }

        return when (session.tag) {
            HEALTH_STEPS_TAG -> processStepsData(payload, session.itemSize)
            HEALTH_OVERLAY_TAG -> processOverlayData(payload, session.itemSize)
            HEALTH_SLEEP_TAG -> {
                // Sleep data is sent as overlay data with sleep types
                logger.d { "Received sleep tag data, processing as overlay" }
                processOverlayData(payload, session.itemSize)
            }
            HEALTH_HR_TAG -> {
                // Heart rate data is embedded in steps data for newer firmware
                logger.d { "Received standalone HR data (tag 85), currently handled in steps data" }
                null
            }
            else -> {
                logger.w { "Unknown health data tag: ${session.tag}" }
                null
            }
        }
    }

    private suspend fun processStepsData(payload: ByteArray, itemSize: UShort): String? {
        logger.d { "HEALTH_DATA: processStepsData called with ${payload.size} byte payload, itemSize=$itemSize" }
        val records = parseStepsData(payload, itemSize)
        logger.d { "HEALTH_DATA: Parsed ${records.size} step records from payload" }
        if (records.isEmpty()) {
            return null
        }

        val totalSteps = records.sumOf { it.steps }
        val totalActiveKcal = records.sumOf { it.activeGramCalories } / 1000
        val totalRestingKcal = records.sumOf { it.restingGramCalories } / 1000
        val totalDistanceKm = records.sumOf { it.distanceCm } / 100000.0
        val totalActiveMin = records.sumOf { it.activeMinutes }
        val heartRateRecords = records.filter { it.heartRate > 0 }
        val avgHeartRate =
            if (heartRateRecords.isNotEmpty()) {
                heartRateRecords.map { it.heartRate }.average().toInt()
            } else 0

        val hrSummary =
            if (heartRateRecords.isNotEmpty()) {
                "avgHR=$avgHeartRate"
            } else "no HR"

        logger.d {
            "HEALTH_DATA: About to insert ${records.size} records into database (steps=$totalSteps, active=${totalActiveKcal}kcal, resting=${totalRestingKcal}kcal, distance=${totalDistanceKm}km, activeMin=$totalActiveMin, $hrSummary)"
        }

        healthDao.insertHealthDataWithPriority(records)
        logger.d { "HEALTH_DATA: Database insertion complete, emitting health update event" }
        _healthDataUpdated.emit(Unit)
        logger.d { "HEALTH_DATA: Health update event emitted successfully" }

        return "${records.size} records (${totalSteps} steps)"
    }

    private suspend fun processOverlayData(payload: ByteArray, itemSize: UShort): String? {
        val records = parseOverlayData(payload, itemSize)
        logger.d { "HEALTH_DATA: Parsed ${records.size} overlay records from payload" }
        if (records.isEmpty()) return null

        healthDao.insertOverlayDataWithDeduplication(records)
        _healthDataUpdated.emit(Unit)

        val totalDurationHours = kotlin.math.round(records.sumOf { it.duration } / 360.0) / 10.0
        return "${records.size} overlay records (${totalDurationHours}h total)"
    }
}

data class HealthSession(
    val tag: UInt,
    val appUuid: Uuid,
    val itemSize: UShort,
)

data class PendingDataItem(
    val payload: ByteArray,
    val itemsLeft: UInt,
)
