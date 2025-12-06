package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import coredev.BlobDatabase
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.health.OverlayType
import io.rebble.libpebblecommon.packets.DataLoggingIncomingPacket
import io.rebble.libpebblecommon.packets.HealthSyncOutgoingPacket
import io.rebble.libpebblecommon.packets.blobdb.BlobCommand
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.app.AppRunStateService
import io.rebble.libpebblecommon.services.blobdb.BlobDBService
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.uuid.Uuid

class HealthService(
        private val protocolHandler: PebbleProtocolHandler,
        private val scope: ConnectionCoroutineScope,
        private val healthDao: HealthDao,
        private val appRunStateService: AppRunStateService,
        private val blobDBService: BlobDBService,
) : ProtocolService {
    private val logger = Logger.withTag("HealthService")
    private val healthSessions = mutableMapOf<UByte, HealthSession>()
    private val isAppOpen = MutableStateFlow(false)
    private var lastHealthStatsUpdateTimeSec = 0L
    private var lastHealthDataTimestamp: Long? = null

    fun init() {
        listenForHealthUpdates()
        startPeriodicSyncLoop()
        scope.launch {
            logger.i { "Initializing health service, requesting health sync" }
            fetchHealthData()
            updateHealthStatsIfNeeded(force = true)
        }
    }

    /**
     * Fetches health data from the watch.
     *
     * @param lastSyncTime The time of the last successful sync, in epoch seconds. If this is the
     * first sync, this should be 0.
     */
    fun fetchHealthData(lastSyncTime: UInt = 0u) {
        scope.launch {
            var effectiveLastSync = lastSyncTime
            var firstSync = lastSyncTime == 0u

            if (firstSync) {
                // If we already have any health data, treat this as a follow-up sync using the
                // latest stored timestamp instead of re-requesting full history.
                val latestTimestampMs = healthDao.getLatestTimestamp()
                if (latestTimestampMs != null && latestTimestampMs > 0) {
                    effectiveLastSync = (latestTimestampMs / 1000L).toUInt()
                    firstSync = false
                    logger.i {
                        "Health data already present; using last timestamp=$effectiveLastSync instead of full first sync"
                    }
                }
            }

            val currentTime = kotlin.time.Clock.System.now().epochSeconds.toUInt()

            // Do not sync if the last sync was less than a minute ago
            if (!firstSync && effectiveLastSync > 0u && (currentTime - effectiveLastSync) < 60u) {
                logger.d { "Skipping health sync, last sync was less than a minute ago." }
                return@launch
            }

            val packet =
                    if (firstSync) {
                        // This is the first sync: request all available history
                        logger.i { "Requesting FIRST health sync (full history, timestamp=0)" }
                        HealthSyncOutgoingPacket.RequestFirstSync(0u)
                    } else {
                        // Subsequent sync, request data since the last sync time
                        val timeSinceLastSync = currentTime - effectiveLastSync
                        logger.i {
                            "Requesting health sync for last ${timeSinceLastSync}s (from timestamp=$effectiveLastSync to $currentTime)"
                        }
                        HealthSyncOutgoingPacket.RequestSync(timeSinceLastSync)
                    }
            logger.d { "Health sync packet sent: ${packet::class.simpleName}" }
            protocolHandler.send(packet)
        }
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
                    }
                }
                .launchIn(scope)
    }

    private fun startPeriodicSyncLoop() {
        scope.launch {
            isAppOpen.distinctUntilChanged { old, new -> old == new }
                    .collectLatest { appOpen ->
                        val intervalMs = if (appOpen) ONE_MINUTE_MS else FIFTEEN_MINUTES_MS
                        while (true) {
                            fetchHealthData()
                            updateHealthStatsIfNeeded()
                            delay(intervalMs)
                        }
                    }
        }
    }

    private fun handleSessionOpen(packet: DataLoggingIncomingPacket.OpenSession) {
        val tag = packet.tag.get()
        val sessionId = packet.sessionId.get()
        if (tag !in HEALTH_TAGS) return

        val applicationUuid = packet.applicationUUID.get()
        val itemSize = packet.dataItemSize.get()
        healthSessions[sessionId] = HealthSession(tag, applicationUuid, itemSize)
        logger.i {
            "Health data session opened (session=$sessionId, tag=$tag ${tagName(tag)}, app=$applicationUuid, itemSize=$itemSize)"
        }
    }

    private fun handleSendDataItems(packet: DataLoggingIncomingPacket.SendDataItems) {
        val sessionId = packet.sessionId.get()
        val session = healthSessions[sessionId] ?: return

        val payload = packet.payload.get().toByteArray()
        val payloadSize = payload.size
        val itemsLeft = packet.itemsLeftAfterThis.get()
        val summary = summarizePayload(session, payload)
        logger.i {
            buildString {
                append("Health update received (session=$sessionId, tag=${session.tag} ${tagName(session.tag)}, ")
                append("bytes=$payloadSize, itemsLeftAfterThis=$itemsLeft")
                if (summary != null) {
                    append(", $summary")
                }
                append(")")
            }
        }
    }

    private fun handleSessionClose(packet: DataLoggingIncomingPacket.CloseSession) {
        val sessionId = packet.sessionId.get()
        healthSessions.remove(sessionId)?.let { session ->
            logger.d {
                "Health data session closed (session=$sessionId, tag=${session.tag} ${tagName(session.tag)})"
            }
        }
    }

    private fun tagName(tag: UInt): String =
            when (tag) {
                HEALTH_STEPS_TAG -> "(steps)"
                HEALTH_SLEEP_TAG -> "(sleep)"
                HEALTH_OVERLAY_TAG -> "(overlay)"
                HEALTH_HR_TAG -> "(hr)"
                else -> "(unknown)"
            }

    private fun summarizePayload(session: HealthSession, payload: ByteArray): String? {
        return when (session.tag) {
            HEALTH_STEPS_TAG -> summarizeStepsPayload(session.itemSize.toInt(), payload)
            HEALTH_OVERLAY_TAG, HEALTH_SLEEP_TAG -> summarizeOverlayPayload(session.itemSize.toInt(), payload)
            HEALTH_HR_TAG -> summarizeHeartRatePayload(session.itemSize.toInt(), payload)
            else -> null
        }
    }

    private fun summarizeStepsPayload(itemSize: Int, payload: ByteArray): String? {
        if (payload.isEmpty() || itemSize <= 0) return null
        val buffer = DataBuffer(payload.toUByteArray())
        var totalSteps = 0
        val heartRateSamples = mutableListOf<Int>()
        var records = 0
        var firstTimestamp: UInt? = null
        var lastTimestamp: UInt? = null

        while (buffer.readPosition < payload.size) {
            val itemStart = buffer.readPosition
            val version = buffer.getUShort()
            val timestamp = buffer.getUInt()
            buffer.getByte() // unused
            buffer.getUByte() // recordLength (unused)
            val recordNum = buffer.getUByte()
            var currentTimestamp = timestamp
            records++

            repeat(recordNum.toInt()) {
                totalSteps += buffer.getUByte().toInt()
                buffer.getUByte() // orientation
                buffer.getUShort() // intensity
                buffer.getUByte() // light

                if (version >= VERSION_FW_3_10_AND_BELOW) {
                    buffer.getUByte() // flags
                }

                if (version >= VERSION_FW_3_11) {
                    buffer.getUShort() // resting calories
                    buffer.getUShort() // active calories
                    buffer.getUShort() // distance
                }

                var heartRate = 0
                if (version >= VERSION_FW_4_0) {
                    heartRate = buffer.getUByte().toInt()
                }

                if (version >= VERSION_FW_4_1) {
                    buffer.getUShort() // heartRateWeight
                }

                if (version >= VERSION_FW_4_3) {
                    buffer.getUByte() // heartRateZone
                }

                if (heartRate > 0) heartRateSamples.add(heartRate)
                if (firstTimestamp == null) firstTimestamp = currentTimestamp
                lastTimestamp = currentTimestamp
                currentTimestamp += 60u
            }

            val consumed = buffer.readPosition - itemStart
            val expected = itemSize
            if (consumed < expected) {
                val skip = expected - consumed
                buffer.getBytes(skip)
            } else if (consumed > expected) {
                // If we over-read, bail out to avoid spinning
                break
            }
        }

        val hrSummary =
                if (heartRateSamples.isNotEmpty()) {
                    val min = heartRateSamples.minOrNull()
                    val max = heartRateSamples.maxOrNull()
                    val avg = heartRateSamples.average().toInt()
                    "hr[min=$min,max=$max,avg=$avg]"
                } else {
                    "no HR samples"
                }

        val range = if (firstTimestamp != null && lastTimestamp != null) {
            "range=${firstTimestamp}-${lastTimestamp}"
        } else {
            "range=unknown"
        }

        return "records=$records, steps=$totalSteps, $hrSummary, $range"
    }

    private fun summarizeOverlayPayload(itemSize: Int, payload: ByteArray): String? {
        if (payload.isEmpty() || itemSize <= 0) return null
        val buffer = DataBuffer(payload.toUByteArray()).apply { setEndian(Endian.Little) }
        var sleepMinutes = 0
        var overlays = 0
        var firstStart: UInt? = null
        var lastStart: UInt? = null

        while (buffer.readPosition < payload.size) {
            val itemStart = buffer.readPosition
            val version = buffer.getUShort()
            buffer.getUShort() // unused
            val rawType = buffer.getUShort().toInt()
            val type = OverlayType.fromValue(rawType)
            buffer.getUInt() // offsetUTC
            val startTime = buffer.getUInt()
            val duration = buffer.getUInt()

            if (version >= 3.toUShort() && (type == OverlayType.Walk || type == OverlayType.Run)) {
                buffer.getUShort() // steps
                buffer.getUShort() // restingKiloCalories
                buffer.getUShort() // activeKiloCalories
                buffer.getUShort() // distanceCm
            } else if (version == 3.toUShort()) {
                buffer.getBytes(8) // calories/distance even for non-walk/run in v3
            }

            overlays++
            if (type != null && type.isSleep()) {
                sleepMinutes += (duration / 60u).toInt()
            }
            if (firstStart == null) firstStart = startTime
            lastStart = startTime

            val consumed = buffer.readPosition - itemStart
            val expected = itemSize
            if (consumed < expected) {
                buffer.getBytes(expected - consumed)
            } else if (consumed > expected) {
                break
            }
        }

        return "overlays=$overlays, sleepMinutes=$sleepMinutes, range=${firstStart}-${lastStart}"
    }

    private fun summarizeHeartRatePayload(itemSize: Int, payload: ByteArray): String? {
        if (payload.isEmpty() || itemSize <= 0) return null
        val buffer = DataBuffer(payload.toUByteArray()).apply { setEndian(Endian.Little) }
        val hrValues = mutableListOf<Int>()
        while (buffer.readPosition < payload.size) {
            val remaining = payload.size - buffer.readPosition
            if (remaining <= 0) break
            hrValues.add(buffer.getUByte().toInt())
            if (itemSize > 1 && remaining >= itemSize) {
                buffer.getBytes(itemSize - 1)
            } else if (itemSize > 1) {
                buffer.getBytes(remaining - 1)
            }
        }
        if (hrValues.isEmpty()) return null
        val min = hrValues.minOrNull()
        val max = hrValues.maxOrNull()
        val avg = hrValues.average().toInt()
        return "hr[min=$min,max=$max,avg=$avg,samples=${hrValues.size}]"
    }

    private data class HealthSession(val tag: UInt, val appUuid: Uuid, val itemSize: UShort)

    private fun OverlayType.isSleep(): Boolean =
            this == OverlayType.Sleep || this == OverlayType.DeepSleep || this == OverlayType.Nap || this == OverlayType.DeepNap

    private suspend fun updateHealthStatsIfNeeded(force: Boolean = false) {
        val latestTimestamp = healthDao.getLatestTimestamp()
        if (latestTimestamp == null || latestTimestamp <= 0) {
            logger.d { "Skipping health stats update; no health data available" }
            return
        }

        val now = kotlin.time.Clock.System.now().epochSeconds
        val shouldUpdate =
                force ||
                        lastHealthDataTimestamp == null ||
                        latestTimestamp > (lastHealthDataTimestamp ?: -1) ||
                        (now - lastHealthStatsUpdateTimeSec) >= SECONDS_IN_DAY

        if (!shouldUpdate) return

        val updated = updateHealthStats(latestTimestamp)
        lastHealthStatsUpdateTimeSec = now
        lastHealthDataTimestamp = latestTimestamp
        if (!updated) {
            logger.d { "Health stats update attempt finished without any writes" }
        }
    }

    private suspend fun updateHealthStats(latestTimestamp: Long): Boolean {
        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = HEALTH_STATS_AVERAGE_DAYS))
        val daysInRange = startDate.daysUntil(today)
        if (daysInRange <= 0) {
            logger.w { "Health stats range invalid (start=$startDate end=$today)" }
            return false
        }

        val startEpoch = startDate.startOfDayEpochSeconds(timeZone)
        val endEpoch = today.startOfDayEpochSeconds(timeZone)

        val averageSteps =
                ((healthDao.getTotalStepsExclusiveEnd(startEpoch, endEpoch) ?: 0L) / daysInRange)
                        .toInt()
                        .coerceAtLeast(0)
        val averageSleepSeconds =
                ((healthDao.getOverlayDuration(startEpoch, endEpoch, OverlayType.Sleep.value)
                        ?: 0L) / daysInRange)
                        .toInt()
                        .coerceAtLeast(0)

        val monthlyStepsSent = sendAverageMonthlySteps(averageSteps)
        val monthlySleepSent = sendAverageMonthlySleep(averageSleepSeconds)
        val movementSent = sendWeeklyMovementData(today, timeZone)

        if (monthlyStepsSent || monthlySleepSent || movementSent) {
            logger.i {
                "Health stats updated (latestTimestamp=$latestTimestamp, avgSteps=$averageSteps, avgSleepSec=$averageSleepSeconds)"
            }
        }

        return monthlyStepsSent || monthlySleepSent || movementSent
    }

    private suspend fun sendWeeklyMovementData(endDateInclusive: LocalDate, timeZone: TimeZone): Boolean {
        var anySent = false
        repeat(MOVEMENT_HISTORY_DAYS) { offset ->
            val day = endDateInclusive.minus(DatePeriod(days = offset))
            val key = MOVEMENT_KEYS[day.dayOfWeek] ?: return@repeat
            val start = day.startOfDayEpochSeconds(timeZone)
            val end = day.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)
            val aggregates = healthDao.getAggregatedHealthData(start, end)
            val payload = movementPayload(start, aggregates)
            val sent = sendHealthStat(key, payload)
            anySent = anySent || sent
        }
        return anySent
    }

    private fun movementPayload(dayStartEpochSec: Long, aggregates: HealthAggregates?): UByteArray {
        val buffer = DataBuffer(MOVEMENT_PAYLOAD_SIZE).apply { setEndian(Endian.Little) }
        buffer.putUInt(HEALTH_STATS_VERSION)
        buffer.putUInt(dayStartEpochSec.toUInt())
        buffer.putUInt((aggregates?.steps ?: 0L).safeUInt())
        buffer.putUInt((aggregates?.activeGramCalories ?: 0L).kilocalories().safeUInt())
        buffer.putUInt((aggregates?.restingGramCalories ?: 0L).kilocalories().safeUInt())
        buffer.putUInt((aggregates?.distanceCm ?: 0L).kilometers().safeUInt())
        buffer.putUInt((aggregates?.activeMinutes ?: 0L).safeUInt())
        return buffer.array()
    }

    private suspend fun sendAverageMonthlySteps(steps: Int): Boolean =
            sendHealthStat(KEY_AVERAGE_DAILY_STEPS, encodeUInt(steps.coerceAtLeast(0).toUInt()))

    private suspend fun sendAverageMonthlySleep(seconds: Int): Boolean =
            sendHealthStat(KEY_AVERAGE_SLEEP_DURATION, encodeUInt(seconds.coerceAtLeast(0).toUInt()))

    private suspend fun sendHealthStat(key: String, payload: UByteArray): Boolean {
        val response =
                withTimeoutOrNull(HEALTH_STATS_BLOB_TIMEOUT_MS) {
                    blobDBService.send(
                            BlobCommand.InsertCommand(
                                    token = randomToken(),
                                    database = BlobDatabase.HealthStats,
                                    key = key.encodeToByteArray().toUByteArray(),
                                    value = payload,
                            )
                    )
                }
        val status = response?.responseValue ?: BlobResponse.BlobStatus.WatchDisconnected
        val success = status == BlobResponse.BlobStatus.Success
        if (!success) {
            logger.w { "Failed to write health stat '$key' (status=$status)" }
        }
        return success
    }

    private fun Long.kilocalories(): Long = this / 1000L
    private fun Long.kilometers(): Long = this / 100000L
    private fun Long.safeUInt(): UInt =
            this.coerceAtLeast(0L).coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt()

    private fun encodeUInt(value: UInt): UByteArray {
        val buffer = DataBuffer(UInt.SIZE_BYTES).apply { setEndian(Endian.Little) }
        buffer.putUInt(value)
        return buffer.array()
    }

    private fun LocalDate.startOfDayEpochSeconds(timeZone: TimeZone): Long =
            this.atStartOfDayIn(timeZone).epochSeconds

    private fun randomToken(): UShort =
            Random.nextInt(0, UShort.MAX_VALUE.toInt()).toUShort()

    companion object {
        private val HEALTH_TAGS = setOf(HEALTH_STEPS_TAG, HEALTH_SLEEP_TAG, HEALTH_OVERLAY_TAG, HEALTH_HR_TAG)
        private const val HEALTH_STEPS_TAG: UInt = 81u
        private const val HEALTH_SLEEP_TAG: UInt = 83u
        private const val HEALTH_OVERLAY_TAG: UInt = 84u
        private const val HEALTH_HR_TAG: UInt = 85u
        private const val ONE_MINUTE_MS = 60_000L
        private const val FIFTEEN_MINUTES_MS = 15 * ONE_MINUTE_MS
        private const val HEALTH_STATS_AVERAGE_DAYS = 30
        private const val MOVEMENT_HISTORY_DAYS = 7
        private const val HEALTH_STATS_BLOB_TIMEOUT_MS = 5_000L
        private const val SECONDS_IN_DAY = 86_400L
        private const val MOVEMENT_PAYLOAD_SIZE = UInt.SIZE_BYTES * 7
        private const val HEALTH_STATS_VERSION: UInt = 1u
        private const val KEY_AVERAGE_DAILY_STEPS = "average_dailySteps"
        private const val KEY_AVERAGE_SLEEP_DURATION = "average_sleepDuration"
        private val MOVEMENT_KEYS =
                mapOf(
                        DayOfWeek.MONDAY to "monday_movementData",
                        DayOfWeek.TUESDAY to "tuesday_movementData",
                        DayOfWeek.WEDNESDAY to "wednesday_movementData",
                        DayOfWeek.THURSDAY to "thursday_movementData",
                        DayOfWeek.FRIDAY to "friday_movementData",
                        DayOfWeek.SATURDAY to "saturday_movementData",
                        DayOfWeek.SUNDAY to "sunday_movementData",
                )
        private val VERSION_FW_3_10_AND_BELOW: UShort = 5u
        private val VERSION_FW_3_11: UShort = 6u
        private val VERSION_FW_4_0: UShort = 7u
        private val VERSION_FW_4_1: UShort = 8u
        private val VERSION_FW_4_3: UShort = 13u
    }
}
