package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.datalogging.HealthDataProcessor
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.health.HealthDebugStats
import io.rebble.libpebblecommon.packets.HealthSyncIncomingPacket
import io.rebble.libpebblecommon.packets.HealthSyncOutgoingPacket
import io.rebble.libpebblecommon.services.app.AppRunStateService
import io.rebble.libpebblecommon.services.blobdb.BlobDBService
import io.rebble.libpebblecommon.connection.ConnectedPebble.Health
import kotlin.time.Clock.System
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Handles health data synchronization between watch and phone.
 *
 * Key responsibilities:
 * - Receiving health data from the watch (steps, sleep, activities, heart rate)
 * - Storing health data in the database with conflict resolution
 * - Sending health statistics back to the watch (averages, daily summaries)
 * - Managing sync lifecycle and preventing battery drain
 *
 * Sync behavior:
 * - Automatic sync on connection
 * - No throttling when app is in foreground (immediate sync)
 * - 30-minute throttle when app is in background (battery saving)
 * - Full stats sync on connection or once per 12 hours
 * - Daily stats update runs every 24 hours
 * - Immediate "today" updates when receiving new data
 *
 * Conflict resolution:
 * - Steps: "highest step count wins" strategy
 * - Phone database is source of truth during reconciliation
 */
class HealthService(
    private val protocolHandler: PebbleProtocolHandler,
    private val scope: ConnectionCoroutineScope,
    private val healthDao: HealthDao,
    private val appRunStateService: AppRunStateService,
    private val blobDBService: BlobDBService,
    private val healthStatDao: HealthStatDao,
    private val healthDataProcessor: HealthDataProcessor,
) : ProtocolService, Health {
    companion object {
        private val logger = Logger.withTag("HealthService")

        private val HEALTH_STATS_AVERAGE_DAYS = 30
        private val MORNING_WAKE_HOUR = 7 // 7 AM for daily stats update
        private val BACKGROUND_SYNC_THROTTLE_MS = 30.minutes.inWholeMilliseconds // 30 minutes
    }

    private val _healthUpdateFlow = MutableSharedFlow<Unit>(replay = 0)
    override val healthUpdateFlow: SharedFlow<Unit> = _healthUpdateFlow

    private var lastSyncRequestTime = 0L
    private var lastDataReceivedTime = 0L
    private var watchAppRunning = false

    fun init() {
        listenForHealthUpdates()
        startPeriodicStatsUpdate()

        // Forward health data updates from HealthDataProcessor to our own flow
        scope.launch {
            healthDataProcessor.healthDataUpdated.collect {
                lastDataReceivedTime = System.now().toEpochMilliseconds()
                _healthUpdateFlow.emit(Unit)
            }
        }

        // Track app state on watch for sync request throttling
        scope.launch {
            appRunStateService.runningApp.collect { runningApp ->
                watchAppRunning = runningApp != null
            }
        }
    }

    /**
     * Request health data from the watch.
     * @param fullSync If true, requests all historical data. If false, requests data since last
     * sync.
     */
    override suspend fun requestHealthData(fullSync: Boolean) {
        sendHealthDataRequest(fullSync)
    }

    /** Manually push the latest averaged health stats to the connected watch. */
    override suspend fun sendHealthAveragesToWatch() {
        logger.d { "HEALTH_STATS: Manual health averages send requested" }
        updateHealthStats()
    }


    private suspend fun sendHealthDataRequest(fullSync: Boolean) {
        val packet =
            if (fullSync) {
                logger.d { "HEALTH_SERVICE: Requesting FULL health data sync from watch" }
                HealthSyncOutgoingPacket.RequestFirstSync(
                    System.now().epochSeconds.toUInt()
                )
            } else {
                val lastSync = healthDao.getLatestTimestamp() ?: 0L
                val currentTime = System.now().epochSeconds
                val timeSinceLastSync =
                    if (lastSync > 0) {
                        (currentTime - (lastSync / 1000)).coerceAtLeast(60)
                    } else {
                        0
                    }

                logger.d {
                    "HEALTH_SERVICE: Requesting incremental health data sync (last ${timeSinceLastSync}s)"
                }
                HealthSyncOutgoingPacket.RequestSync(timeSinceLastSync.toUInt())
            }
        protocolHandler.send(packet)
    }


    private fun listenForHealthUpdates() {
        protocolHandler
            .inboundMessages
            .onEach { packet ->
                when (packet) {
                    is HealthSyncIncomingPacket -> handleHealthSyncRequest(packet)
                }
            }
            .launchIn(scope)
    }

    private fun handleHealthSyncRequest(packet: HealthSyncIncomingPacket) {
        val now = System.now().toEpochMilliseconds()
        val timeSinceLastSync = now - lastSyncRequestTime
        val timeSinceLastData = now - lastDataReceivedTime

        // Smarter throttling to avoid data loss:
        // - Always accept if watch app is running (user is actively using watch)
        // - Always accept if we haven't received data in a while (avoid buffer overflow on watch)
        // - Otherwise throttle to save battery
        val shouldThrottle = !watchAppRunning &&
                           timeSinceLastSync < BACKGROUND_SYNC_THROTTLE_MS &&
                           timeSinceLastData < BACKGROUND_SYNC_THROTTLE_MS

        if (shouldThrottle) {
            logger.d {
                "HEALTH_SYNC: Throttling sync request - watch idle, last sync ${timeSinceLastSync / 60_000}min ago, last data ${timeSinceLastData / 60_000}min ago"
            }
            return
        }

        logger.d {
            "HEALTH_SYNC: Watch requested health sync (payload=${packet.payload.size} bytes, app_running=$watchAppRunning)"
        }

        lastSyncRequestTime = now
        scope.launch { sendHealthDataRequest(fullSync = false) }
    }

    private fun startPeriodicStatsUpdate() {
        scope.launch {
            // Update health stats once daily at 7 AM
            while (true) {
                val timeZone = TimeZone.currentSystemDefault()
                val now = System.now().toLocalDateTime(timeZone)

                // Calculate next morning update time (7 AM tomorrow)
                val tomorrow = now.date.plus(DatePeriod(days = 1))
                val nextMorning =
                        LocalDateTime(
                            tomorrow.year,
                            tomorrow.month,
                            tomorrow.dayOfMonth,
                            MORNING_WAKE_HOUR,
                            0,
                            0
                        )
                val morningInstant = nextMorning.toInstant(timeZone)
                val delayUntilMorning = (morningInstant.toEpochMilliseconds() - System.now().toEpochMilliseconds()).coerceAtLeast(0L)

                logger.d { "HEALTH_STATS: Next scheduled update at $nextMorning (${delayUntilMorning / (60 * 60 * 1000)}h from now)" }
                delay(delayUntilMorning)

                logger.d { "HEALTH_STATS: Running scheduled daily stats update" }
                updateHealthStats()
            }
        }
    }


    private suspend fun updateHealthStats() {
        val latestTimestamp = healthDao.getLatestTimestamp()
        if (latestTimestamp == null || latestTimestamp <= 0) {
            logger.d { "Skipping health stats update; no health data available" }
            return
        }

        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = HEALTH_STATS_AVERAGE_DAYS))

        val updated = updateHealthStatsInDatabase(healthDao, healthStatDao, today, startDate, timeZone)
        if (!updated) {
            logger.d { "Health stats update attempt finished without any writes" }
        } else {
            logger.d { "Health stats updated (latestTimestamp=$latestTimestamp)" }
        }
    }
}
