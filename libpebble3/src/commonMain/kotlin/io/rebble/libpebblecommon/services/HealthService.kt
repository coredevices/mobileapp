package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.insertHealthDataWithPriority
import io.rebble.libpebblecommon.database.dao.insertOverlayDataWithDeduplication
import io.rebble.libpebblecommon.database.dao.removeDuplicateOverlayEntries
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.DataLoggingIncomingPacket
import io.rebble.libpebblecommon.packets.HealthSyncIncomingPacket
import io.rebble.libpebblecommon.packets.HealthSyncOutgoingPacket
import io.rebble.libpebblecommon.services.app.AppRunStateService
import io.rebble.libpebblecommon.services.blobdb.BlobDBService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlin.uuid.Uuid

/**
 * Handles health data synchronization between watch and phone.
 *
 * Key responsibilities:
 * - Receiving health data from the watch (steps, sleep, activities, heart rate)
 * - Storing health data in the database with conflict resolution
 * - Sending health statistics back to the watch (averages, daily summaries)
 * - Managing sync lifecycle and preventing battery drain
 *
 * Battery optimization:
 * - Full stats sync only on connection or once per 12 hours
 * - Daily stats update runs every 24 hours
 * - Incremental "today" updates when receiving new data
 *
 * Conflict resolution:
 * - Steps: "highest step count wins" strategy
 * - Sleep: Phone database is source of truth, watch sleep data is ignored
 */
class HealthService(
    private val protocolHandler: PebbleProtocolHandler,
    private val scope: ConnectionCoroutineScope,
    private val healthDao: HealthDao,
    private val appRunStateService: AppRunStateService,
    private val blobDBService: BlobDBService,
    private val healthServiceRegistry: io.rebble.libpebblecommon.health.HealthServiceRegistry,
) : ProtocolService {
    private val healthSessions = mutableMapOf<UByte, HealthSession>()
    private val isAppOpen = MutableStateFlow(false)
    private val isHealthScreenActive = MutableStateFlow(false) // Track if HealthScreen is currently open
    private val lastFullStatsUpdate = MutableStateFlow(0L) // Epoch millis of last full stats push
    private val lastTodayUpdateDate = MutableStateFlow<LocalDate?>(null) // Date of last today movement update
    private val lastTodayUpdateTime = MutableStateFlow(0L) // Epoch millis of last today update
    private val lastDataReceptionTime = MutableStateFlow(0L) // Epoch millis of last data reception from watch

    companion object {
        private val logger = Logger.withTag("HealthService")

        private val HEALTH_TAGS = setOf(HEALTH_STEPS_TAG, HEALTH_SLEEP_TAG, HEALTH_OVERLAY_TAG, HEALTH_HR_TAG)
        private const val HEALTH_STEPS_TAG: UInt = 81u
        private const val HEALTH_SLEEP_TAG: UInt = 83u
        private const val HEALTH_OVERLAY_TAG: UInt = 84u
        private const val HEALTH_HR_TAG: UInt = 85u

        private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60_000L
        private const val HEALTH_STATS_AVERAGE_DAYS = 30
        private const val HEALTH_SYNC_WAIT_MS = 8_000L
        private const val HEALTH_SYNC_POLL_MS = 1000L // Reduced from 500ms to save battery
        private const val RECONCILE_DELAY_MS = 1000L
        private const val FULL_STATS_THROTTLE_HOURS = 12L
        private const val TODAY_UPDATE_THROTTLE_MS = 30 * 60_000L // 30 minutes minimum between today updates
        private const val MORNING_WAKE_HOUR = 7 // 7 AM for daily stats update
    }

    fun init() {
        // Register this service instance so it can be accessed for manual sync requests
        healthServiceRegistry.register(this)

        listenForHealthUpdates()
        startPeriodicStatsUpdate()

        // Trigger smart sync when watch connects/is selected
        scope.launch {
            logger.i { "HEALTH_SERVICE: Watch connected - performing smart reconciliation" }
            reconcileWatchWithDatabase()
        }

        // Unregister when the connection scope is cancelled
        scope.launch {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                healthServiceRegistry.unregister(this@HealthService)
            }
        }
    }

    /**
     * Request health data from the watch.
     * @param fullSync If true, requests all historical data. If false, requests data since last sync.
     */
    fun requestHealthData(fullSync: Boolean = false) {
        if (!isActiveConnection("health data request")) return
        scope.launch {
            sendHealthDataRequest(fullSync)
        }
    }

    /**
     * Manually push the latest averaged health stats to the connected watch.
     */
    fun sendHealthAveragesToWatch() {
        if (!isActiveConnection("manual health averages push")) return
        scope.launch {
            logger.i { "HEALTH_STATS: Manual health averages send requested" }
            updateHealthStats()
            lastFullStatsUpdate.value = kotlin.time.Clock.System.now().toEpochMilliseconds()
        }
    }

    /**
     * Called when HealthScreen becomes active (user opens the health tab).
     * Enables real-time data reception from watch.
     */
    fun setHealthScreenActive(active: Boolean) {
        if (!isActiveConnection("health screen state change")) return
        isHealthScreenActive.value = active
        if (active) {
            logger.i { "HEALTH_SERVICE: HealthScreen opened - enabling real-time updates" }
        } else {
            logger.i { "HEALTH_SERVICE: HealthScreen closed - throttling to background mode (30min)" }
        }
    }

    /**
     * Force a complete health data overwrite on the watch, ignoring the 12-hour throttle.
     * Useful for debugging or when you know the watch has incorrect data.
     */
    fun forceHealthDataOverwrite() {
        if (!isActiveConnection("force overwrite request")) return
        scope.launch {
            logger.i { "HEALTH_STATS: Force overwrite requested - pushing all health data to watch" }

            // First, clean up any duplicate overlay entries in the database
            healthDao.removeDuplicateOverlayEntries()

            val timeZone = TimeZone.currentSystemDefault()
            val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
            lastTodayUpdateDate.value = null
            updateHealthStats()
            lastFullStatsUpdate.value = kotlin.time.Clock.System.now().toEpochMilliseconds()
            lastTodayUpdateDate.value = today
        }
    }

    /**
     * Get current health statistics for debugging
     */
    suspend fun getHealthDebugStats(): HealthDebugStats {
        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = HEALTH_STATS_AVERAGE_DAYS))

        val todayStart = today.startOfDayEpochSeconds(timeZone)
        val todayEnd = today.plus(kotlinx.datetime.DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)

        val averages = calculateHealthAverages(healthDao, startDate, today, timeZone)
        val todaySteps = healthDao.getTotalStepsExclusiveEnd(todayStart, todayEnd) ?: 0L
        val latestTimestamp = healthDao.getLatestTimestamp()

        val daysOfData = maxOf(averages.stepDaysWithData, averages.sleepDaysWithData)

        return HealthDebugStats(
            totalSteps30Days = averages.totalSteps,
            averageStepsPerDay = averages.averageStepsPerDay,
            totalSleepSeconds30Days = averages.totalSleepSeconds,
            averageSleepSecondsPerDay = averages.averageSleepSecondsPerDay,
            todaySteps = todaySteps,
            latestDataTimestamp = latestTimestamp,
            daysOfData = daysOfData
        )
    }

    private fun isActiveConnection(reason: String): Boolean {
        val active = healthServiceRegistry.isActive(this)
        if (!active) {
            logger.d { "HEALTH_SERVICE: Ignoring $reason because this watch is not the active connection" }
        }
        return active
    }

    private suspend fun reconcileWatchWithDatabase() {
        if (!isActiveConnection("reconcile")) return
        val baselineTimestamp = healthDao.getLatestTimestamp() ?: 0L
        val isFirstSync = baselineTimestamp == 0L
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val hoursSinceLastFullUpdate = (now - lastFullStatsUpdate.value) / (60 * 60_000L)

        logger.i {
            "HEALTH_SYNC: Reconciling on connection (baseline=$baselineTimestamp, isFirstSync=$isFirstSync, hoursSinceLastFullUpdate=$hoursSinceLastFullUpdate)"
        }

        // Always request any new data from the watch first
        sendHealthDataRequest(fullSync = false)
        val newDataArrived = waitForNewerHealthData(baselineTimestamp)

        if (newDataArrived) {
            logger.i { "HEALTH_SYNC: Newer health data pulled from watch and merged with database" }
            // Wait to ensure all async database writes complete before we read back for stats
            delay(RECONCILE_DELAY_MS)
        }

        // Push full stats to watch on connection (assume watch may have switched or been reset)
        // This overwrites the watch with our database, which now contains the merged best data
        // But throttle to avoid excessive updates if connection drops/reconnects frequently
        if (hoursSinceLastFullUpdate >= FULL_STATS_THROTTLE_HOURS) {
            logger.i { "HEALTH_SYNC: Pushing merged database stats to watch (treating as new/switched watch)" }
            // Reset today's update flag so it can be updated again if needed
            lastTodayUpdateDate.value = null
            updateHealthStats()
            lastFullStatsUpdate.value = now
            // Mark that we've updated today so we don't do it again immediately
            val timeZone = TimeZone.currentSystemDefault()
            val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
            lastTodayUpdateDate.value = today
        } else {
            logger.i { "HEALTH_SYNC: Skipping full stats push - recently updated ${hoursSinceLastFullUpdate}h ago (prevents reconnection spam)" }
        }
    }

    private suspend fun sendHealthDataRequest(fullSync: Boolean) {
        val packet = if (fullSync) {
            logger.i { "HEALTH_SERVICE: Requesting FULL health data sync from watch" }
            HealthSyncOutgoingPacket.RequestFirstSync(
                kotlin.time.Clock.System.now().epochSeconds.toUInt()
            )
        } else {
            val lastSync = healthDao.getLatestTimestamp() ?: 0L
            val currentTime = kotlin.time.Clock.System.now().epochSeconds
            val timeSinceLastSync = if (lastSync > 0) {
                (currentTime - (lastSync / 1000)).coerceAtLeast(60)
            } else {
                0
            }

            logger.i { "HEALTH_SERVICE: Requesting incremental health data sync (last ${timeSinceLastSync}s)" }
            HealthSyncOutgoingPacket.RequestSync(timeSinceLastSync.toUInt())
        }

        protocolHandler.send(packet)
    }

    private suspend fun waitForNewerHealthData(previousLatest: Long): Boolean {
        val baseline = previousLatest.coerceAtLeast(0L)
        return withTimeoutOrNull(HEALTH_SYNC_WAIT_MS) {
            while (true) {
                delay(HEALTH_SYNC_POLL_MS)
                val latest = healthDao.getLatestTimestamp() ?: 0L

                val hasNewer = if (baseline == 0L) {
                    latest > 0L
                } else {
                    latest > baseline
                }

                if (hasNewer) return@withTimeoutOrNull true
            }
            false
        } ?: false
    }

    private fun listenForHealthUpdates() {
        appRunStateService.runningApp
            .map { it != null }
            .distinctUntilChanged { old, new -> old == new }
            .onEach { isAppOpen.value = it }
            .launchIn(scope)

        protocolHandler.inboundMessages
            .onEach { packet ->
                when (packet) {
                    is DataLoggingIncomingPacket.OpenSession -> handleSessionOpen(packet)
                    is DataLoggingIncomingPacket.SendDataItems -> handleSendDataItems(packet)
                    is DataLoggingIncomingPacket.CloseSession -> handleSessionClose(packet)
                    is HealthSyncIncomingPacket -> handleHealthSyncRequest(packet)
                }
            }
            .launchIn(scope)
    }

    private fun handleHealthSyncRequest(packet: HealthSyncIncomingPacket) {
        logger.i { "HEALTH_SYNC: Watch requested health sync (payload=${packet.payload.size} bytes)" }
        if (!isActiveConnection("health sync request")) return
        scope.launch {
            sendHealthDataRequest(fullSync = false)
        }
    }

    private fun startPeriodicStatsUpdate() {
        scope.launch {
            // Update health stats once daily, preferably in the morning
            while (true) {
                val timeZone = TimeZone.currentSystemDefault()
                val now = kotlin.time.Clock.System.now().toLocalDateTime(timeZone)
                val lastUpdateTime = lastFullStatsUpdate.value
                val hoursSinceLastUpdate = if (lastUpdateTime > 0) {
                    (kotlin.time.Clock.System.now().toEpochMilliseconds() - lastUpdateTime) / (60 * 60_000L)
                } else {
                    24L
                }

                // Calculate next morning update time (7 AM)
                val tomorrow = now.date.plus(DatePeriod(days = 1))
                val nextMorning = kotlinx.datetime.LocalDateTime(
                    tomorrow.year, tomorrow.month, tomorrow.dayOfMonth,
                    MORNING_WAKE_HOUR, 0, 0
                )
                val morningInstant = nextMorning.toInstant(timeZone)
                val delayUntilMorning = (morningInstant.toEpochMilliseconds() -
                                         kotlin.time.Clock.System.now().toEpochMilliseconds())
                    .coerceAtLeast(0L)

                // Wait until morning or 24 hours, whichever comes first
                val delayTime = minOf(delayUntilMorning, TWENTY_FOUR_HOURS_MS)
                delay(delayTime)

                // Only update if it's been at least 24 hours since last update
                if (hoursSinceLastUpdate >= 24) {
                    if (!isActiveConnection("scheduled daily stats update")) continue
                    logger.i { "HEALTH_STATS: Running scheduled daily stats update (${hoursSinceLastUpdate}h since last)" }
                    updateHealthStats()
                    lastFullStatsUpdate.value = kotlin.time.Clock.System.now().toEpochMilliseconds()
                }
            }
        }
    }

    private fun handleSessionOpen(packet: DataLoggingIncomingPacket.OpenSession) {
        if (!isActiveConnection("health session open")) return
        val tag = packet.tag.get()
        val sessionId = packet.sessionId.get()
        if (tag !in HEALTH_TAGS) return

        val applicationUuid = packet.applicationUUID.get()
        val itemSize = packet.dataItemSize.get()
        healthSessions[sessionId] = HealthSession(tag, applicationUuid, itemSize)
        logger.i {
            "HEALTH_SESSION: Opened session $sessionId for ${tagName(tag)} (tag=$tag, itemSize=$itemSize bytes)"
        }
    }

    private fun handleSendDataItems(packet: DataLoggingIncomingPacket.SendDataItems) {
        if (!isActiveConnection("incoming health data")) return
        val sessionId = packet.sessionId.get()
        val session = healthSessions[sessionId] ?: return

        // Throttle data reception if HealthScreen is not active
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val timeSinceLastReception = now - lastDataReceptionTime.value
        val healthScreenActive = isHealthScreenActive.value

        if (!healthScreenActive && timeSinceLastReception < TODAY_UPDATE_THROTTLE_MS) {
            logger.d {
                "HEALTH_DATA: Throttling data reception - HealthScreen inactive and last reception was ${timeSinceLastReception / 60_000}min ago (need ${TODAY_UPDATE_THROTTLE_MS / 60_000}min)"
            }
            return
        }

        lastDataReceptionTime.value = now

        val payload = packet.payload.get().toByteArray()
        val payloadSize = payload.size
        val itemsLeft = packet.itemsLeftAfterThis.get()
        val summary = summarizePayload(session, payload)
        logger.i {
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

        // Process and store the health data in the database
        scope.launch {
            processHealthData(session, payload)

            // Update today's movement and recent sleep data when we finish receiving a batch
            // Throttled to max once per 30 minutes to reduce battery drain
            if (itemsLeft.toInt() == 0) {
                val timeZone = TimeZone.currentSystemDefault()
                val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val timeSinceLastUpdate = now - lastTodayUpdateTime.value

                val shouldUpdate = lastTodayUpdateDate.value != today ||
                                   timeSinceLastUpdate >= TODAY_UPDATE_THROTTLE_MS

                if (shouldUpdate) {
                    logger.d { "HEALTH_DATA: Received new data, updating today's movement and recent sleep data (last update ${timeSinceLastUpdate / 60_000}min ago)" }
                    sendTodayMovementData(healthDao, blobDBService, today, timeZone)
                    sendRecentSleepData(healthDao, blobDBService, today, timeZone)
                    lastTodayUpdateDate.value = today
                    lastTodayUpdateTime.value = now
                } else {
                    logger.d { "HEALTH_DATA: Skipping today update - throttled (last update ${timeSinceLastUpdate / 60_000}min ago, need ${TODAY_UPDATE_THROTTLE_MS / 60_000}min)" }
                }
            }
        }
    }

    private fun handleSessionClose(packet: DataLoggingIncomingPacket.CloseSession) {
        if (!isActiveConnection("health session close")) return
        val sessionId = packet.sessionId.get()
        healthSessions.remove(sessionId)?.let { session ->
            logger.i {
                "HEALTH_SESSION: Closed session $sessionId for ${tagName(session.tag)}"
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

    private fun summarizePayload(session: HealthSession, payload: ByteArray): String? {
        return when (session.tag) {
            HEALTH_STEPS_TAG -> summarizeStepsPayload(session.itemSize.toInt(), payload)
            HEALTH_OVERLAY_TAG, HEALTH_SLEEP_TAG -> summarizeOverlayPayload(session.itemSize.toInt(), payload)
            HEALTH_HR_TAG -> summarizeHeartRatePayload(session.itemSize.toInt(), payload)
            else -> null
        }
    }

    private suspend fun processHealthData(session: HealthSession, payload: ByteArray) {
        // Only process health data from the system app
        if (session.appUuid != SYSTEM_APP_UUID) {
            logger.d { "Ignoring health data from non-system app: ${session.appUuid}" }
            return
        }

        when (session.tag) {
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
            }

            else -> logger.w { "Unknown health data tag: ${session.tag}" }
        }
    }

    private suspend fun processStepsData(payload: ByteArray, itemSize: UShort) {
        val records = parseStepsData(payload, itemSize)
        if (records.isEmpty()) return

        val totalSteps = records.sumOf { it.steps }
        val totalActiveKcal = records.sumOf { it.activeGramCalories } / 1000
        val totalRestingKcal = records.sumOf { it.restingGramCalories } / 1000
        val totalDistanceKm = records.sumOf { it.distanceCm } / 100000.0
        val totalActiveMin = records.sumOf { it.activeMinutes }
        val heartRateRecords = records.filter { it.heartRate > 0 }
        val avgHeartRate = if (heartRateRecords.isNotEmpty()) {
            heartRateRecords.map { it.heartRate }.average().toInt()
        } else 0

        logger.i {
            val distKm = (totalDistanceKm * 100).toInt() / 100.0
            "HEALTH_DATA: Received ${records.size} step records - steps=$totalSteps, activeKcal=$totalActiveKcal, restingKcal=$totalRestingKcal, distance=${distKm}km, activeMin=$totalActiveMin, avgHR=$avgHeartRate"
        }
        logger.d {
            "HEALTH_DATA: Time range: ${records.firstOrNull()?.timestamp}-${records.lastOrNull()?.timestamp}"
        }
        healthDao.insertHealthDataWithPriority(records)
        logger.d { "HEALTH_DATA: Inserted ${records.size} health records into database" }
    }

    private suspend fun processOverlayData(payload: ByteArray, itemSize: UShort) {
        val records = parseOverlayData(payload, itemSize)
        if (records.isEmpty()) return

        val sleepRecords = records.filter { type ->
            val overlayType = io.rebble.libpebblecommon.health.OverlayType.fromValue(type.type)
            overlayType != null && isSleepType(overlayType)
        }
        val totalSleepMinutes = sleepRecords.sumOf { (it.duration / 60).toInt() }
        val totalSleepHours = totalSleepMinutes / 60.0

        val activityRecords = records.filter { type ->
            val overlayType = io.rebble.libpebblecommon.health.OverlayType.fromValue(type.type)
            overlayType == io.rebble.libpebblecommon.health.OverlayType.Walk || overlayType == io.rebble.libpebblecommon.health.OverlayType.Run
        }
        val activitySteps = activityRecords.sumOf { it.steps }
        val activityDistanceKm = activityRecords.sumOf { it.distanceCm } / 100000.0

        logger.i {
            val sleepHrs = (totalSleepHours * 10).toInt() / 10.0
            val distKm = (activityDistanceKm * 100).toInt() / 100.0
            "HEALTH_DATA: Received ${records.size} overlay records - sleep=${sleepRecords.size} (${sleepHrs}h), activities=${activityRecords.size} (steps=$activitySteps, distance=${distKm}km)"
        }
        healthDao.insertOverlayDataWithDeduplication(records)
        logger.d { "HEALTH_DATA: Processed ${records.size} overlay records (see deduplication summary above)" }
    }

    private fun isSleepType(type: io.rebble.libpebblecommon.health.OverlayType): Boolean =
        type == io.rebble.libpebblecommon.health.OverlayType.Sleep ||
                type == io.rebble.libpebblecommon.health.OverlayType.DeepSleep ||
                type == io.rebble.libpebblecommon.health.OverlayType.Nap ||
                type == io.rebble.libpebblecommon.health.OverlayType.DeepNap

    private suspend fun updateHealthStats() {
        if (!isActiveConnection("health stats update")) return
        val latestTimestamp = healthDao.getLatestTimestamp()
        if (latestTimestamp == null || latestTimestamp <= 0) {
            logger.d { "Skipping health stats update; no health data available" }
            return
        }

        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = HEALTH_STATS_AVERAGE_DAYS))

        val updated = sendHealthStatsToWatch(healthDao, blobDBService, today, startDate, timeZone)
        if (!updated) {
            logger.d { "Health stats update attempt finished without any writes" }
        } else {
            logger.i { "Health stats updated (latestTimestamp=$latestTimestamp)" }
        }
    }

    private data class HealthSession(val tag: UInt, val appUuid: Uuid, val itemSize: UShort)
}

data class HealthDebugStats(
    val totalSteps30Days: Long,
    val averageStepsPerDay: Int,
    val totalSleepSeconds30Days: Long,
    val averageSleepSecondsPerDay: Int,
    val todaySteps: Long,
    val latestDataTimestamp: Long?,
    val daysOfData: Int
)

private fun kotlinx.datetime.LocalDate.startOfDayEpochSeconds(timeZone: TimeZone): Long =
    this.atStartOfDayIn(timeZone).epochSeconds
