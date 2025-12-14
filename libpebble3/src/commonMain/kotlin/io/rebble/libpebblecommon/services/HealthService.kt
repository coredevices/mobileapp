package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import coredev.BlobDatabase
import io.rebble.libpebblecommon.SystemAppIDs.SYSTEM_APP_UUID
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.insertHealthDataWithPriority
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
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
        private val healthServiceRegistry: io.rebble.libpebblecommon.health.HealthServiceRegistry,
) : ProtocolService {
    private val logger = Logger.withTag("HealthService")
    private val healthSessions = mutableMapOf<UByte, HealthSession>()
    private val isAppOpen = MutableStateFlow(false)

    fun init() {
        // Register this service instance so it can be accessed for manual sync requests
        healthServiceRegistry.register(this)

        listenForHealthUpdates()
        startPeriodicStatsUpdate()
        scope.launch {
            logger.i { "HEALTH_SERVICE: Initializing and sending initial stats to watch" }
            updateHealthStats()
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
        scope.launch {
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
    }

    /**
     * Manually push the latest averaged health stats to the connected watch.
     */
    fun sendHealthAveragesToWatch() {
        scope.launch {
            logger.i { "HEALTH_STATS: Manual health averages send requested" }
            updateHealthStats()
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
        val todayEnd = today.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)

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

    private fun startPeriodicStatsUpdate() {
        scope.launch {
            // Update health stats periodically - the watch will broadcast data when ready
            while (true) {
                delay(FIFTEEN_MINUTES_MS)
                updateHealthStats()
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
            "HEALTH_SESSION: Opened session $sessionId for ${tagName(tag)} (tag=$tag, itemSize=$itemSize bytes)"
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

            // Update health stats after receiving new data
            if (itemsLeft.toInt() == 0) {
                // This was the last batch of data, update stats
                updateHealthStats()
            }
        }
    }

    private fun handleSessionClose(packet: DataLoggingIncomingPacket.CloseSession) {
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
        if (payload.isEmpty() || itemSize.toInt() == 0) {
            logger.w { "Cannot process steps data: empty payload or zero item size" }
            return
        }

        val buffer = DataBuffer(payload.toUByteArray())
        val records = mutableListOf<HealthDataEntity>()

        if (payload.size % itemSize.toInt() != 0) {
            logger.w {
                "Steps payload size (${payload.size}) is not a multiple of item size ($itemSize); parsing what we can"
            }
        }

        val packetCount = payload.size / itemSize.toInt()
        logger.d { "Processing $packetCount steps packets" }

        for (i in 0 until packetCount) {
            val itemStart = buffer.readPosition
            buffer.setEndian(Endian.Little)

            val version = buffer.getUShort()
            val timestamp = buffer.getUInt()
            buffer.getByte() // unused
            val recordLength = buffer.getUByte()
            val recordNum = buffer.getUByte()

            if (!SUPPORTED_STEP_VERSIONS.contains(version)) {
                logger.w {
                    "Unsupported health steps record version=$version, skipping remaining payload"
                }
                return
            }

            logger.d {
                "Steps packet $i: version=$version, timestamp=$timestamp, recordLength=$recordLength, recordNum=$recordNum"
            }

            var currentTimestamp = timestamp

            repeat(recordNum.toInt()) {
                val steps = buffer.getUByte().toInt()
                val orientation = buffer.getUByte().toInt()
                val intensity = buffer.getUShort().toInt()
                val lightIntensity = buffer.getUByte().toInt()

                val flags =
                        if (version >= VERSION_FW_3_10_AND_BELOW) {
                            buffer.getUByte().toInt()
                        } else {
                            0
                        }

                var restingGramCalories = 0
                var activeGramCalories = 0
                var distanceCm = 0
                var heartRate = 0
                var heartRateWeight = 0
                var heartRateZone = 0

                if (version >= VERSION_FW_3_11) {
                    restingGramCalories = buffer.getUShort().toInt()
                    activeGramCalories = buffer.getUShort().toInt()
                    distanceCm = buffer.getUShort().toInt()
                }

                if (version >= VERSION_FW_4_0) {
                    heartRate = buffer.getUByte().toInt()
                }

                if (version >= VERSION_FW_4_1) {
                    heartRateWeight = buffer.getUShort().toInt()
                }

                if (version >= VERSION_FW_4_3) {
                    heartRateZone = buffer.getUByte().toInt()
                }

                records.add(
                        HealthDataEntity(
                                timestamp = currentTimestamp.toLong(),
                                steps = steps,
                                orientation = orientation,
                                intensity = intensity,
                                lightIntensity = lightIntensity,
                                activeMinutes = if ((flags and 2) > 0) 1 else 0,
                                restingGramCalories = restingGramCalories,
                                activeGramCalories = activeGramCalories,
                                distanceCm = distanceCm,
                                heartRate = heartRate,
                                heartRateZone = heartRateZone,
                                heartRateWeight = heartRateWeight
                        )
                )

                currentTimestamp += 60u
            }

            val consumed = buffer.readPosition - itemStart
            val expected = itemSize.toInt()
            if (consumed < expected) {
                val skip = expected - consumed
                buffer.getBytes(skip)
                logger.d { "Skipped $skip padding bytes in steps packet $i" }
            } else if (consumed > expected) {
                logger.w { "Health steps item over-read: consumed=$consumed, expected=$expected" }
            }
        }

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
        if (payload.isEmpty() || itemSize.toInt() == 0) {
            logger.w { "Cannot process overlay data: empty payload or zero item size" }
            return
        }

        val buffer = DataBuffer(payload.toUByteArray())
        buffer.setEndian(Endian.Little)
        val records = mutableListOf<OverlayDataEntity>()

        if (payload.size % itemSize.toInt() != 0) {
            logger.w {
                "Overlay payload size (${payload.size}) is not a multiple of item size ($itemSize); parsing what we can"
            }
        }

        val packetCount = payload.size / itemSize.toInt()
        logger.d { "Processing $packetCount overlay packets" }

        for (i in 0 until packetCount) {
            val itemStart = buffer.readPosition

            val version = buffer.getUShort()
            buffer.getUShort() // unused
            val rawType = buffer.getUShort().toInt()
            val type = OverlayType.fromValue(rawType)

            if (type == null) {
                val remaining = itemSize.toInt() - 6 // already consumed 6 bytes
                if (remaining > 0) buffer.getBytes(remaining)
                logger.w { "Unknown overlay type: $rawType, skipping packet $i" }
                continue
            }

            val offsetUTC = buffer.getUInt()
            val startTime = buffer.getUInt()
            val duration = buffer.getUInt()

            var steps = 0
            var restingKiloCalories = 0
            var activeKiloCalories = 0
            var distanceCm = 0

            if (version < 3.toUShort() || (type != OverlayType.Walk && type != OverlayType.Run)) {
                if (version == 3.toUShort()) {
                    // Firmware 3.x includes calorie/distance data even for non-walk/run types
                    buffer.getBytes(8)
                }
            } else {
                steps = buffer.getUShort().toInt()
                restingKiloCalories = buffer.getUShort().toInt()
                activeKiloCalories = buffer.getUShort().toInt()
                distanceCm = buffer.getUShort().toInt()
            }

            records.add(
                    OverlayDataEntity(
                            startTime = startTime.toLong(),
                            duration = duration.toLong(),
                            type = type.value,
                            steps = steps,
                            restingKiloCalories = restingKiloCalories,
                            activeKiloCalories = activeKiloCalories,
                            distanceCm = distanceCm,
                            offsetUTC = offsetUTC.toInt()
                    )
            )

            val consumed = buffer.readPosition - itemStart
            val expected = itemSize.toInt()
            if (consumed < expected) {
                val skip = expected - consumed
                buffer.getBytes(skip)
                logger.d { "Skipped $skip padding bytes in overlay packet $i" }
            } else if (consumed > expected) {
                logger.w { "Health overlay item over-read: consumed=$consumed, expected=$expected" }
            }
        }

        val sleepRecords = records.filter { type ->
            val overlayType = OverlayType.fromValue(type.type)
            overlayType != null && overlayType.isSleep()
        }
        val totalSleepMinutes = sleepRecords.sumOf { (it.duration / 60).toInt() }
        val totalSleepHours = totalSleepMinutes / 60.0

        val activityRecords = records.filter { type ->
            val overlayType = OverlayType.fromValue(type.type)
            overlayType == OverlayType.Walk || overlayType == OverlayType.Run
        }
        val activitySteps = activityRecords.sumOf { it.steps }
        val activityDistanceKm = activityRecords.sumOf { it.distanceCm } / 100000.0

        logger.i {
            val sleepHrs = (totalSleepHours * 10).toInt() / 10.0
            val distKm = (activityDistanceKm * 100).toInt() / 100.0
            "HEALTH_DATA: Received ${records.size} overlay records - sleep=${sleepRecords.size} (${sleepHrs}h), activities=${activityRecords.size} (steps=$activitySteps, distance=${distKm}km)"
        }
        healthDao.insertOverlayData(records)
        logger.d { "HEALTH_DATA: Inserted ${records.size} overlay records into database" }
    }

    private suspend fun updateHealthStats() {
        val latestTimestamp = healthDao.getLatestTimestamp()
        if (latestTimestamp == null || latestTimestamp <= 0) {
            logger.d { "Skipping health stats update; no health data available" }
            return
        }

        val updated = sendHealthStatsToWatch()
        if (!updated) {
            logger.d { "Health stats update attempt finished without any writes" }
        } else {
            logger.i { "Health stats updated (latestTimestamp=$latestTimestamp)" }
        }
    }

    private suspend fun sendHealthStatsToWatch(): Boolean {
        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = HEALTH_STATS_AVERAGE_DAYS))
        val averages = calculateHealthAverages(healthDao, startDate, today, timeZone)
        if (averages.rangeDays <= 0) {
            logger.w { "HEALTH_STATS: Invalid date range (start=$startDate end=$today)" }
            return false
        }

        val averageSleepHours = averages.averageSleepSecondsPerDay / 3600.0

        logger.i {
            "HEALTH_STATS: 30-day averages window $startDate to $today (range=${averages.rangeDays} days, step days=${averages.stepDaysWithData}, sleep days=${averages.sleepDaysWithData})"
        }
        logger.i {
            "HEALTH_STATS: Average daily steps = ${averages.averageStepsPerDay} (total: ${averages.totalSteps} steps)"
        }
        logger.i {
            val sleepHrs = (averageSleepHours * 10).toInt() / 10.0
            "HEALTH_STATS: Average sleep = ${sleepHrs} hours (${averages.averageSleepSecondsPerDay} seconds, total: ${averages.totalSleepSeconds} seconds)"
        }

        val monthlyStepsSent = sendAverageMonthlySteps(averages.averageStepsPerDay)
        val monthlySleepSent = sendAverageMonthlySleep(averages.averageSleepSecondsPerDay)
        val movementSent = sendWeeklyMovementData(today, timeZone)

        val sentCount = listOf(monthlyStepsSent, monthlySleepSent, movementSent).count { it }
        if (sentCount > 0) {
            logger.i { "HEALTH_STATS: Successfully sent $sentCount stat categories to watch" }
        } else {
            logger.w { "HEALTH_STATS: Failed to send any stats to watch" }
        }

        return monthlyStepsSent || monthlySleepSent || movementSent
    }

    private suspend fun sendWeeklyMovementData(endDateInclusive: LocalDate, timeZone: TimeZone): Boolean {
        logger.i { "HEALTH_STATS: Sending weekly movement data for last $MOVEMENT_HISTORY_DAYS days" }
        var anySent = false
        var successCount = 0

        repeat(MOVEMENT_HISTORY_DAYS) { offset ->
            val day = endDateInclusive.minus(DatePeriod(days = offset))
            val dayName = day.dayOfWeek.name
            val key = MOVEMENT_KEYS[day.dayOfWeek] ?: return@repeat
            val start = day.startOfDayEpochSeconds(timeZone)
            val end = day.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)
            val aggregates = healthDao.getAggregatedHealthData(start, end)

            val steps = aggregates?.steps ?: 0L
            val activeKcal = (aggregates?.activeGramCalories ?: 0L) / 1000
            val restingKcal = (aggregates?.restingGramCalories ?: 0L) / 1000
            val distanceKm = (aggregates?.distanceCm ?: 0L) / 100000.0
            val activeMin = aggregates?.activeMinutes ?: 0L
            val activeSec = activeMin * 60

            logger.i {
                val distKm = (distanceKm * 100).toInt() / 100.0
                "HEALTH_STATS: $dayName ($day): steps=$steps, activeKcal=$activeKcal, restingKcal=$restingKcal, distance=${distKm}km, activeSec=$activeSec (${activeMin}min)"
            }

            val payload = movementPayload(start, aggregates)
            val sent = sendHealthStat(key, payload)
            if (sent) {
                successCount++
                logger.d { "HEALTH_STATS: Successfully sent $dayName movement data to watch" }
            } else {
                logger.w { "HEALTH_STATS: Failed to send $dayName movement data to watch" }
            }
            anySent = anySent || sent
        }

        logger.i { "HEALTH_STATS: Weekly movement data: sent $successCount/$MOVEMENT_HISTORY_DAYS days successfully" }
        return anySent
    }

    private fun movementPayload(dayStartEpochSec: Long, aggregates: HealthAggregates?): UByteArray {
        val buffer = DataBuffer(MOVEMENT_PAYLOAD_SIZE).apply { setEndian(Endian.Little) }
        val steps = (aggregates?.steps ?: 0L).safeUInt()
        val activeKcal = (aggregates?.activeGramCalories ?: 0L).kilocalories().safeUInt()
        val restingKcal = (aggregates?.restingGramCalories ?: 0L).kilocalories().safeUInt()
        val distanceKm = (aggregates?.distanceCm ?: 0L).kilometers().safeUInt()
        val activeSec = (aggregates?.activeMinutes ?: 0L).toSeconds().safeUInt()

        buffer.putUInt(HEALTH_STATS_VERSION)
        buffer.putUInt(dayStartEpochSec.toUInt())
        buffer.putUInt(steps)
        buffer.putUInt(activeKcal)
        buffer.putUInt(restingKcal)
        buffer.putUInt(distanceKm)
        buffer.putUInt(activeSec)

        logger.d {
            "HEALTH_STATS: Movement payload - version=$HEALTH_STATS_VERSION, timestamp=$dayStartEpochSec, steps=$steps, activeKcal=$activeKcal, restingKcal=$restingKcal, distanceKm=$distanceKm, activeSec=$activeSec"
        }

        return buffer.array()
    }

    private suspend fun sendAverageMonthlySteps(steps: Int): Boolean {
        val result = sendHealthStat(KEY_AVERAGE_DAILY_STEPS, encodeUInt(steps.coerceAtLeast(0).toUInt()))
        if (result) {
            logger.i { "HEALTH_STATS: Sent average daily steps to watch: $steps steps/day" }
        } else {
            logger.w { "HEALTH_STATS: Failed to send average daily steps to watch" }
        }
        return result
    }

    private suspend fun sendAverageMonthlySleep(seconds: Int): Boolean {
        val hours = seconds / 3600.0
        val result = sendHealthStat(KEY_AVERAGE_SLEEP_DURATION, encodeUInt(seconds.coerceAtLeast(0).toUInt()))
        if (result) {
            val hrs = (hours * 10).toInt() / 10.0
            logger.i { "HEALTH_STATS: Sent average sleep duration to watch: ${hrs} hours ($seconds seconds/day)" }
        } else {
            logger.w { "HEALTH_STATS: Failed to send average sleep duration to watch" }
        }
        return result
    }

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
            logger.w { "HEALTH_STATS: BlobDB write failed for '$key' (status=$status)" }
        }
        return success
    }

    private fun Long.kilocalories(): Long = this / 1000L
    private fun Long.kilometers(): Long = this / 100000L
    private fun Long.toSeconds(): Long = this * 60L
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
        private const val FIFTEEN_MINUTES_MS = 15 * 60_000L
        private const val HEALTH_STATS_AVERAGE_DAYS = 30
        private const val MOVEMENT_HISTORY_DAYS = 7
        private const val HEALTH_STATS_BLOB_TIMEOUT_MS = 5_000L
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
        private val SUPPORTED_STEP_VERSIONS =
                setOf(
                        VERSION_FW_3_10_AND_BELOW,
                        VERSION_FW_3_11,
                        VERSION_FW_4_0,
                        VERSION_FW_4_1,
                        VERSION_FW_4_3
                )
    }
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
