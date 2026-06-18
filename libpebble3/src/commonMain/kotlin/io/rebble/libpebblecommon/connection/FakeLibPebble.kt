package io.rebble.libpebblecommon.connection

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.paging.PagingSource
import io.ktor.util.PlatformUtils
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.calls.Call
import io.rebble.libpebblecommon.connection.bt.BluetoothState
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.endpointmanager.InstalledLanguagePack
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.CustomTimelineActionHandler
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.AppWithCount
import io.rebble.libpebblecommon.database.dao.ChannelAndCount
import io.rebble.libpebblecommon.database.dao.ContactWithCount
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.dao.DailyMovementAggregate
import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.services.DailySleep
import io.rebble.libpebblecommon.connection.LatestHeartRate
import io.rebble.libpebblecommon.database.entity.HRMonitoringInterval
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.database.entity.NotificationEntity
import io.rebble.libpebblecommon.database.entity.NotificationRuleEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.database.entity.TimelineNotification
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.health.HealthDebugStats
import io.rebble.libpebblecommon.health.HealthSettings
import io.rebble.libpebblecommon.js.PKJSApp
import io.rebble.libpebblecommon.locker.AppBasicProperties
import io.rebble.libpebblecommon.locker.AppPlatform
import io.rebble.libpebblecommon.locker.AppProperties
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.LockerWrapper
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.music.MusicAction
import io.rebble.libpebblecommon.music.PlaybackState
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.notification.NotificationDecision
import io.rebble.libpebblecommon.notification.VibePattern
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.weather.WeatherLocationData
import io.rebble.libpebblecommon.web.LockerEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.io.files.Path
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeLibPebble : LibPebble {
    override fun init() {
        // No-op
    }

    override val watches: PebbleDevices = MutableStateFlow(fakeWatches())
    override val connectionEvents: Flow<PebbleConnectionEvent> = MutableSharedFlow()

    override fun watchesDebugState(): String {
        return ""
    }

    override val config: StateFlow<LibPebbleConfig> = MutableStateFlow(LibPebbleConfig())

    override fun updateConfig(config: LibPebbleConfig) {
        // No-op
    }

    override suspend fun sendNotification(
        notification: TimelineNotification,
        actionHandlers: Map<UByte, CustomTimelineActionHandler>?
    ) {
        // No-op
    }

    override suspend fun markNotificationRead(itemId: Uuid) {
        // No-op
    }

    override suspend fun sendPing(cookie: UInt) {
        // No-op
    }

    override suspend fun launchApp(uuid: Uuid) {
        // No-op
    }

    override suspend fun stopApp(uuid: Uuid) {
        // No-op
    }

    override fun doStuffAfterPermissionsGranted() {
        // No-op
    }

    override fun checkForFirmwareUpdates() {
    }

    override suspend fun updateTimeIfNeeded() {
    }

    // Scanning interface
    override val bluetoothEnabled: StateFlow<BluetoothState> =
        MutableStateFlow(BluetoothState.Enabled)

    override val isScanningBle: StateFlow<Boolean> = MutableStateFlow(false)
    override val isScanningClassic: StateFlow<Boolean> = MutableStateFlow(false)

    override fun startBleScan() {
        // No-op
    }

    override fun stopBleScan() {
        // No-op
    }

    override fun startClassicScan() {
        // No-op
    }

    override fun stopClassicScan() {
        // No-op
    }

    // RequestSync interface
    override fun requestLockerSync(): Deferred<Unit> {
        return CompletableDeferred(Unit)
    }

    // LockerApi interface
    override suspend fun sideloadApp(pbwPath: Path): Boolean {
        // No-op
        return true
    }

    override fun getAllLockerBasicInfo(): Flow<List<AppBasicProperties>> {
        return flow { emit(emptyList()) }
    }

    override fun getAllLockerUuids(): Flow<List<Uuid>> {
        return flow { emit(emptyList()) }
    }

    val locker = MutableStateFlow(fakeLockerEntries)

    override fun getLocker(
        type: AppType,
        searchQuery: String?,
        limit: Int
    ): Flow<List<LockerWrapper>> {
        return locker
    }

    override fun getLockerApp(id: Uuid): Flow<LockerWrapper?> {
        return flow { emit(fakeLockerEntries.first()) }
    }

    override suspend fun setAppOrder(id: Uuid, order: Int) {

    }

    override suspend fun waitUntilAppSyncedToWatch(
        id: Uuid,
        identifier: PebbleIdentifier,
        timeout: Duration,
    ): Boolean = true

    override suspend fun removeApp(id: Uuid): Boolean = true
    override suspend fun addAppToLocker(app: LockerEntry) {
    }

    override suspend fun addAppsToLocker(apps: List<LockerEntry>) {
    }

    override fun restoreSystemAppOrder() {
    }

    override val activeWatchface: StateFlow<LockerWrapper?>
        get() = MutableStateFlow(fakeLockerEntry())

    private val _notificationApps = MutableStateFlow(fakeNotificationApps)

    override fun notificationApps(): Flow<List<AppWithCount>> =
        _notificationApps.map { it.map { AppWithCount(it, 44) } }

    override fun notificationAppChannelCounts(packageName: String): Flow<List<ChannelAndCount>> =
        MutableStateFlow(emptyList())

    override fun mostRecentNotificationsFor(
        pkg: String?,
        channelId: String?,
        contactId: String?,
        limit: Int
    ): Flow<List<NotificationEntity>> = flow {
        emit(fakeNotifications)
    }

    override fun mostRecentNotificationParticipants(limit: Int): Flow<List<String>> {
        return flow {
            emit(
                listOf(
                    "Alice",
                    "Bob Smith",
                    "Charlie Johnson",
                    "David Williams",
                    "Eve Jones",
                )
            )
        }
    }

    private val fakeNotifications by lazy { fakeNotifications() }

    private fun fakeNotifications(): List<NotificationEntity> {
        return buildList {
            for (i in 1..25) {
                add(fakeNotification())
            }
        }
    }

    private fun fakeNotification(): NotificationEntity {
        return NotificationEntity(
            pkg = randomName(),
            key = randomName(),
            groupKey = randomName(),
            channelId = randomName(),
            timestamp = Instant.DISTANT_PAST.asMillisecond(),
            title = randomName(),
            body = randomName(),
            decision = NotificationDecision.SendToWatch,
        )
    }

    override fun updateNotificationAppMuteState(packageName: String?, muteState: MuteState) {
        // No-op
    }

    override fun updateNotificationAppState(
        packageName: String,
        vibePatternName: String?,
        colorName: String?,
        iconName: String?,
    ) {
        TODO("Not yet implemented")
    }

    override fun notificationRulesForApp(packageName: String): Flow<List<NotificationRuleEntity>> =
        emptyFlow()

    override fun upsertNotificationRule(rule: NotificationRuleEntity) {
        // No-op
    }

    override fun deleteNotificationRule(rule: NotificationRuleEntity) {
        // No-op
    }

    override fun updateNotificationChannelMuteState(
        packageName: String,
        channelId: String,
        muteState: MuteState
    ) {
        // No-op
    }

    override fun updateNotificationAppAllowDuplicates(packageName: String, allowDuplicates: Boolean) {
        // No-op
    }

    override suspend fun getAppIcon(packageName: String): ImageBitmap? {
        // Return a green square as a placeholder
        val width = 48
        val height = 48
        val buffer = IntArray(width * height) { Color.Green.toArgb() }
        return ImageBitmap(width, height).apply { readPixels(buffer) }
    }

    // CallManagement interface
    override val currentCall: MutableStateFlow<Call?> = MutableStateFlow(null)

    // Calendar interface
    override fun calendars(): Flow<List<CalendarEntity>> {
        return emptyFlow()
    }

    override fun updateCalendarEnabled(calendarId: Int, enabled: Boolean) {
        // No-op
    }

    // OtherPebbleApps interface
    override fun otherPebbleCompanionAppsInstalled(): StateFlow<List<OtherPebbleApp>> =
        MutableStateFlow(emptyList())

    override suspend fun getAccountToken(appUuid: Uuid): String? {
        return ""
    }

    override val userFacingErrors: Flow<UserFacingError>
        get() = flow { }

    override fun getContactsWithCounts(searchTerm: String, onlyNotified: Boolean): PagingSource<Int, ContactWithCount> {
        return TODO()
    }

    override fun getContact(id: String): Flow<ContactWithCount?> {
        TODO("Not yet implemented")
    }

    override fun updateContactState(
        contactId: String,
        muteState: MuteState,
        vibePatternName: String?,
    ) {
    }

    override suspend fun getContactImage(lookupKey: String): ImageBitmap? {
        return null
    }

    override val analyticsEvents: Flow<AnalyticsEvent>
        get() = flow { }
    override val healthSettings: Flow<HealthSettings>
        get() = flow { emit(HealthSettings(
            heightMm = 1700,
            weightDag = 7000,
            trackingEnabled = false,
            activityInsightsEnabled = false,
            sleepInsightsEnabled = false,
            ageYears = 35,
            gender = HealthGender.Female,
            imperialUnits = false,
            hrmEnabled = true,
            hrmMeasurementInterval = HRMonitoringInterval.TenMin,
            hrmActivityTrackingEnabled = false,
            restingHr = 70,
            elevatedHr = 100,
            maxHr = 190,
            hrZone1Threshold = 130,
            hrZone2Threshold = 154,
            hrZone3Threshold = 172,
        )) }

    override fun updateHealthSettings(healthSettings: HealthSettings) {}

    override suspend fun getHealthDebugStats(): HealthDebugStats {
        return HealthDebugStats(
            totalSteps30Days = 0L,
            averageStepsPerDay = 0,
            totalSleepSeconds30Days = 0L,
            averageSleepSecondsPerDay = 0,
            todaySteps = 0L,
            lastNightSleepHours = null,
            latestDataTimestamp = null,
            daysOfData = 0,
            weekdayTypicalSteps = emptyMap(),
        )
    }

    override fun requestHealthData(fullSync: Boolean) {
        // No-op for fake implementation
    }

    override fun sendHealthAveragesToWatch() {
        // No-op for fake implementation
    }

    override val healthDataUpdated: SharedFlow<Unit> = MutableStateFlow(Unit)

    override suspend fun getCurrentPosition(
        maximumAge: Duration?,
        timeout: Duration?,
        highAccuracy: Boolean,
    ): GeolocationPositionResult {
        TODO("Not yet implemented")
    }

    override suspend fun watchPosition(
        interval: Duration,
        highAccuracy: Boolean,
    ): Flow<GeolocationPositionResult> {
        TODO("Not yet implemented")
    }

    override fun insertOrReplace(pin: TimelinePin) {
    }

    override fun delete(pinUuid: Uuid) {
    }

    override fun vibePatterns(): Flow<List<VibePattern>> {
        return flow {
            emit(
                listOf(
                    VibePattern("Test", listOf(100, 200, 300), false),
                    VibePattern("Test2", listOf(100, 200, 300), false),
                    VibePattern("Test3", listOf(100, 200, 300), false),
                )
            )
        }
    }

    override fun addCustomVibePattern(
        name: String,
        pattern: List<Long>
    ) {
        TODO("Not yet implemented")
    }

    override fun deleteCustomPattern(name: String) {
        TODO("Not yet implemented")
    }

    private val _watchPrefs = MutableStateFlow(
        WatchPref.enumeratePrefs().map { WatchPreference(it, null) }
    )

    override val watchPrefs: Flow<List<WatchPreference<*>>> = _watchPrefs.asStateFlow()

    override fun setWatchPref(watchPref: WatchPreference<*>) {
        _watchPrefs.update { current ->
            val index = current.indexOfFirst { it.pref.id == watchPref.pref.id }
            if (index != -1) {
                current.toMutableList().apply { set(index, watchPref) }
            } else {
                current + watchPref
            }
        }
    }

    override fun updateWeatherData(weatherData: List<WeatherLocationData>) {
    }

    override suspend fun getLatestTimestamp(): Long? = 0

    override suspend fun getHealthDataAfter(afterTimestamp: Long): List<HealthDataEntity> = emptyList()

    override suspend fun getOverlayEntriesAfter(
        afterTimestamp: Long,
        types: List<Int>
    ): List<OverlayDataEntity> = emptyList()

    override suspend fun getHealthDataForRange(start: Long, end: Long) = emptyList<HealthDataEntity>()
    override suspend fun getDailyAggregates(start: Long, end: Long) = emptyList<DailyMovementAggregate>()
    override suspend fun getTotalHealthData(start: Long, end: Long): HealthAggregates? = null
    override suspend fun getAverageHeartRate(start: Long, end: Long): Double? = null
    override suspend fun getSleepEntries(start: Long, end: Long) = emptyList<OverlayDataEntity>()
    override suspend fun getDailySleepSession(dayStartEpochSec: Long): DailySleep? = null
    override suspend fun getLatestHeartRateReading(): LatestHeartRate? = null
    override suspend fun getRestingHeartRate(dayStartEpochSec: Long): Int? = null
    override suspend fun getHRZoneMinutes(start: Long, end: Long) = emptyMap<Int, Long>()
    override suspend fun getActivitySessions(start: Long, end: Long) = emptyList<OverlayDataEntity>()
    override suspend fun getTypicalSteps(dayOfWeek: Int) = emptyList<Long>()
    override suspend fun getTypicalSleepSeconds() = 0L
    override suspend fun populateDebugHealthData() {}
}

fun fakeWatches(): List<PebbleDevice> {
    return buildList {
        for (i in 1..8) {
            add(fakeWatch())
        }
    }
}

fun fakeWatch(connected: Boolean = Random.nextBoolean()): PebbleDevice {
    val num = Random.nextInt(1111, 9999)
    val name = "Core $num"
    val fakeIdentifier = if (PlatformUtils.IS_JVM) {
        randomMacAddress().asPebbleBleIdentifier()
    } else {
        Uuid.random().toString().asPebbleBleIdentifier()
    }
    return if (connected) {
        val updating = Random.nextBoolean()
        val fwupState = if (updating) {
            val fakeUpdate = FirmwareUpdateCheckResult.FoundUpdate(
                version = FirmwareVersion.from(
                    "v4.9.9-core1",
                    isRecovery = false,
                    gitHash = "",
                    timestamp = Instant.DISTANT_PAST,
                    isDualSlot = false,
                    isSlot0 = false,
                )!!,
                url = "",
                notes = "v4.9.9-core1 is great",
            )
            FirmwareUpdater.FirmwareUpdateStatus.InProgress(fakeUpdate, MutableStateFlow(0.47f))
        } else {
            FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.Idle()
        }
        val fwupAvailable = if (!updating && Random.nextBoolean()) {
            FirmwareUpdateCheckResult.FoundUpdate(
                version = FirmwareVersion.from(
                    "v4.9.9-core2",
                    isRecovery = false,
                    gitHash = "",
                    timestamp = kotlin.time.Instant.DISTANT_PAST,
                    isDualSlot = false,
                    isSlot0 = false,
                )!!,
                url = "http://something",
                notes = "update!!",
            )
        } else {
            null
        }
        FakeConnectedDevice(
            identifier = fakeIdentifier,
            firmwareUpdateAvailable = FirmwareUpdateCheckState(false, fwupAvailable),
            firmwareUpdateState = fwupState,
            name = name,
            nickname = null,
            connectionFailureInfo = null,
        )
    } else {
        object : DiscoveredPebbleDevice {
            override val identifier = fakeIdentifier
            override val name: String = "Fake 1234"
            override val nickname: String? = "Faker 1234"
            override val connectionFailureInfo: ConnectionFailureInfo? = null

            override fun connect() {
            }
        }
    }
}

class FakeConnectedDevice(
    override val identifier: PebbleIdentifier,
    override val firmwareUpdateAvailable: FirmwareUpdateCheckState,
    override val firmwareUpdateState: FirmwareUpdater.FirmwareUpdateStatus,
    override val name: String,
    override val nickname: String?,
    override val color: WatchColor = run {
        val white = Random.nextBoolean()
        if (white) {
            WatchColor.Pebble2DuoWhite
        } else {
            WatchColor.Pebble2DuoBlack
        }
    },
    override val watchType: WatchHardwarePlatform = WatchHardwarePlatform.CORE_ASTERIX,
    override val lastConnected: Instant = Instant.DISTANT_PAST,
    override val serial: String = "XXXXXXXXXXXX",
    override val runningFwVersion: String = "v1.2.3-core",
    override val connectionFailureInfo: ConnectionFailureInfo?,
    override val usingBtClassic: Boolean = false,
    override val capabilities: Set<ProtocolCapsFlag> = emptySet()
) : ConnectedPebbleDevice {

    override fun forget() {}
    override fun setNickname(nickname: String?) {
    }

    override fun connect() {}

    override fun disconnect() {}

    override suspend fun sendPing(cookie: UInt): UInt = cookie

    override fun resetIntoPrf() {}

    override fun createCoreDump() {}
    override fun factoryReset() {}

    override suspend fun sendPPMessage(bytes: ByteArray) {}

    override suspend fun sendPPMessage(ppMessage: PebblePacket) {}

    override val inboundMessages: Flow<PebblePacket> = MutableSharedFlow()
    override val rawInboundMessages: Flow<ByteArray> = MutableSharedFlow()

    override fun sideloadFirmware(path: Path) {}

    override fun updateFirmware(update: FirmwareUpdateCheckResult.FoundUpdate) {}

    override fun checkforFirmwareUpdate() {}

    override suspend fun launchApp(uuid: Uuid) {}

    override suspend fun stopApp(uuid: Uuid) {}

    override val runningApp: StateFlow<Uuid?> = MutableStateFlow(null)
    override val watchInfo: WatchInfo = WatchInfo(
        runningFwVersion = FirmwareVersion.from(
            runningFwVersion,
            isRecovery = false,
            gitHash = "",
            timestamp = kotlin.time.Instant.DISTANT_PAST,
            isDualSlot = false,
            isSlot0 = false,
        )!!,
        recoveryFwVersion = FirmwareVersion.from(
            runningFwVersion,
            isRecovery = true,
            gitHash = "",
            timestamp = kotlin.time.Instant.DISTANT_PAST,
            isDualSlot = false,
            isSlot0 = false,
        )!!,
        platform = watchType,
        bootloaderTimestamp = kotlin.time.Instant.DISTANT_PAST,
        board = "board",
        serial = serial,
        btAddress = "11:22:33:44:55:66",
        resourceCrc = -9999999,
        resourceTimestamp = kotlin.time.Instant.DISTANT_PAST,
        language = "en-GB",
        languageVersion = 1,
        capabilities = emptySet(),
        isUnfaithful = false,
        healthInsightsVersion = null,
        javascriptVersion = null,
        color = color,
    )

    override suspend fun updateTime() {}
    override suspend fun updateTimeIfNeeded() {}

    override fun inboundAppMessages(appUuid: Uuid): Flow<AppMessageData> {
        return MutableSharedFlow()
    }

    override val transactionSequence: Iterator<UByte> = iterator { }

    override suspend fun sendAppMessage(appMessageData: AppMessageData): AppMessageResult =
        AppMessageResult.ACK(appMessageData.transactionId)

    override suspend fun sendAppMessageResult(appMessageResult: AppMessageResult) {}

    override suspend fun gatherLogs(): Path? = null

    override suspend fun getCoreDump(unread: Boolean): Path? = null

    override suspend fun updateTrack(track: MusicTrack) {}

    override suspend fun updatePlaybackState(
        state: PlaybackState,
        trackPosMs: UInt,
        playbackRatePct: UInt,
        shuffle: Boolean,
        repeatType: RepeatType
    ) {
    }

    override suspend fun updatePlayerInfo(packageId: String, name: String) {}

    override suspend fun updateVolumeInfo(volumePercent: UByte) {}

    override val musicActions: Flow<MusicAction> = MutableSharedFlow()
    override val updateRequestTrigger: Flow<Unit> = MutableSharedFlow()

    @Deprecated("Use more generic currentCompanionAppSession instead and cast if necessary")
    override val currentPKJSSession: StateFlow<PKJSApp?> = MutableStateFlow(null)
    override val currentCompanionAppSessions: StateFlow<List<CompanionApp>> = MutableStateFlow(emptyList())

    override suspend fun startDevConnection() {}
    override suspend fun stopDevConnection() {}
    override val devConnectionActive: StateFlow<Boolean> = MutableStateFlow(false)
    override val batteryLevel: Int? = 50
    override suspend fun takeScreenshot(): ImageBitmap {
        // Return an orange square as a placeholder
        val width = 144
        val height = 168
        val buffer = IntArray(width * height) { Color(0xFFFA4A36).toArgb() }
        return ImageBitmap(width, height).apply { readPixels(buffer) }
    }

    override fun installLanguagePack(path: Path, name: String) {
    }

    override fun installLanguagePack(url: String, name: String) {
    }

    override val languagePackInstallState: LanguagePackInstallState =
        LanguagePackInstallState.Idle()
    override val installedLanguagePack: InstalledLanguagePack? = null

    override suspend fun requestHealthData(fullSync: Boolean): Boolean = true
}

class FakeConnectedDeviceInRecovery(
    override val identifier: PebbleIdentifier,
    override val firmwareUpdateAvailable: FirmwareUpdateCheckState,
    override val firmwareUpdateState: FirmwareUpdater.FirmwareUpdateStatus,
    override val name: String,
    override val nickname: String?,
    override val color: WatchColor = run {
        val white = Random.nextBoolean()
        if (white) {
            WatchColor.Pebble2DuoWhite
        } else {
            WatchColor.Pebble2DuoBlack
        }
    },
    override val watchType: WatchHardwarePlatform = WatchHardwarePlatform.CORE_ASTERIX,
    override val lastConnected: Instant = Instant.DISTANT_PAST,
    override val serial: String = "XXXXXXXXXXXX",
    override val runningFwVersion: String = "v1.2.3-core",
    override val connectionFailureInfo: ConnectionFailureInfo?,
    override val usingBtClassic: Boolean = false,
    override val capabilities: Set<ProtocolCapsFlag> = emptySet(),
) : ConnectedPebbleDeviceInRecovery {

    override fun forget() {}
    override fun setNickname(nickname: String?) {
    }

    override fun connect() {}

    override fun disconnect() {}

    override fun sideloadFirmware(path: Path) {}

    override fun updateFirmware(update: FirmwareUpdateCheckResult.FoundUpdate) {}

    override fun checkforFirmwareUpdate() {}

    override val watchInfo: WatchInfo = WatchInfo(
        runningFwVersion = FirmwareVersion.from(
            runningFwVersion,
            isRecovery = false,
            gitHash = "",
            timestamp = kotlin.time.Instant.DISTANT_PAST,
            isDualSlot = false,
            isSlot0 = false,
        )!!,
        recoveryFwVersion = FirmwareVersion.from(
            runningFwVersion,
            isRecovery = true,
            gitHash = "",
            timestamp = kotlin.time.Instant.DISTANT_PAST,
            isDualSlot = false,
            isSlot0 = false,
        )!!,
        platform = watchType,
        bootloaderTimestamp = kotlin.time.Instant.DISTANT_PAST,
        board = "board",
        serial = serial,
        btAddress = "11:22:33:44:55:66",
        resourceCrc = -9999999,
        resourceTimestamp = kotlin.time.Instant.DISTANT_PAST,
        language = "en-GB",
        languageVersion = 1,
        capabilities = emptySet(),
        isUnfaithful = false,
        healthInsightsVersion = null,
        javascriptVersion = null,
        color = color,
    )

    override suspend fun startDevConnection() {}
    override suspend fun stopDevConnection() {}
    override val devConnectionActive: StateFlow<Boolean> = MutableStateFlow(false)
    override val batteryLevel: Int? = 50

    override suspend fun gatherLogs(): Path? {
        return null
    }

    override suspend fun getCoreDump(unread: Boolean): Path? {
        return null
    }
}

private val fakeNotificationApps by lazy { fakeNotificationApps() }

fun fakeNotificationApps(): List<NotificationAppItem> {
    return buildList {
        for (i in 1..50) {
            add(fakeNotificationApp())
        }
    }
}

fun fakeNotificationApp(): NotificationAppItem {
    return NotificationAppItem(
        name = randomName(),
        packageName = randomName(),
        muteState = if (Random.nextBoolean()) MuteState.Always else MuteState.Never,
        channelGroups = if (Random.nextBoolean()) emptyList() else fakeChannelGroups(),
        stateUpdated = Instant.DISTANT_PAST.asMillisecond(),
        lastNotified = Instant.DISTANT_PAST.asMillisecond(),
        vibePatternName = null,
        colorName = null,
        iconCode = null,
        allowDuplicates = false,
    )
}

fun fakeChannelGroups(): List<ChannelGroup> {
    return buildList {
        for (i in 1..Random.nextInt(2, 5)) {
            add(
                ChannelGroup(
                    id = randomName(),
                    name = randomName(),
                    channels = fakeChannels(),
                )
            )
        }
    }
}

fun fakeChannels(): List<ChannelItem> {
    return buildList {
        for (i in 1..Random.nextInt(1, 8)) {
            add(
                ChannelItem(
                    id = randomName(),
                    name = randomName(),
                    muteState = if (Random.nextBoolean()) MuteState.Always else MuteState.Never,
                )
            )
        }
    }
}

val fakeLockerEntries by lazy { fakeLockerEntries() }

fun fakeLockerEntries(): List<LockerWrapper> {
    return buildList {
        for (i in 1..40) {
            add(fakeLockerEntry())
        }
    }
}

fun randomName(): String {
    val length = Random.nextInt(5, 20)
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length)
        .map { allowedChars[Random.nextInt(0, allowedChars.length)] }
        .joinToString("")
}

fun randomMacAddress(): String {
    val allowedChars = "0123456789ABCDEF"
    return (1..6).joinToString(":") {
        (1..2).map {
            allowedChars[Random.nextInt(
                0,
                allowedChars.length
            )]
        }.joinToString("")
    }
}

fun fakeLockerEntry(): LockerWrapper {
    val appType = if (Random.nextBoolean()) AppType.Watchface else AppType.Watchapp
    return LockerWrapper.NormalApp(
        properties = AppProperties(
            id = Uuid.random(),
            type = appType,
            title = randomName(),
            developerName = "Core Devices",
            platforms = listOf(
                AppPlatform(
                    watchType = WatchType.CHALK,
                    screenshotImageUrl = "https://assets2.rebble.io/180x180/ZiFWSDWHTwearl6RNBNA",
                    listImageUrl = "https://assets2.rebble.io/exact/180x180/LVK5AGVeS1ufpR8NNk7C",
                    iconImageUrl = "",
                ),
                AppPlatform(
                    watchType = WatchType.DIORITE,
                    screenshotImageUrl = "https://assets2.rebble.io/144x168/u8q7BQv0QjGkLXy4WydA",
                    listImageUrl = "https://assets2.rebble.io/exact/144x168/LVK5AGVeS1ufpR8NNk7C",
                    iconImageUrl = "",
                ), AppPlatform(
                    watchType = WatchType.BASALT,
                    screenshotImageUrl = "https://assets2.rebble.io/144x168/LVK5AGVeS1ufpR8NNk7C",
                    listImageUrl = "https://assets2.rebble.io/exact/144x168/LVK5AGVeS1ufpR8NNk7C",
                    iconImageUrl = "",
                ),
                AppPlatform(
                    watchType = WatchType.APLITE,
                    screenshotImageUrl = "https://assets2.rebble.io/144x168/7fNxWcZ3RZ2clRNWA68Q",
                    listImageUrl = "https://assets2.rebble.io/exact/144x168/LVK5AGVeS1ufpR8NNk7C",
                    iconImageUrl = "",
                )
            ),
            version = "1.0",
            hearts = 50,
            category = "fun stuff",
            iosCompanion = null,
            androidCompanion = null,
            order = 0,
            developerId = "123",
            sourceLink = "https://example.com",
            storeId = "6962e51d29173c0009b18f8e",
            capabilities = emptyList(),
        ),
        sideloaded = false,
        configurable = Random.nextBoolean(),
        sync = true,
    )
}

/**
 * [FakeLibPebble] configured for a deterministic set of fake watches.
 * This is used in development builds to power the app without a real Bluetooth connection.
 */
class ConfiguredFakeLibPebble(
    fakeWatches: List<WatchHardwarePlatform>,
    activeWatch: WatchHardwarePlatform = fakeWatches.firstOrNull() ?: WatchHardwarePlatform.CORE_ASTERIX,
) : LibPebble {
    private val delegate = FakeLibPebble()
    override fun init() = delegate.init()
    override val watches: PebbleDevices = MutableStateFlow(
        fakeWatches.mapIndexed { index, hw -> configuredFakeWatch(connected = hw == activeWatch, watchType = hw, index = index) }
    )
    override val connectionEvents: Flow<PebbleConnectionEvent> = MutableSharedFlow()
    override fun watchesDebugState(): String = ""
    override val config: StateFlow<LibPebbleConfig> = MutableStateFlow(LibPebbleConfig())
    override fun updateConfig(config: LibPebbleConfig) = delegate.updateConfig(config)
    override suspend fun sendNotification(notification: TimelineNotification, actionHandlers: Map<UByte, CustomTimelineActionHandler>?) = delegate.sendNotification(notification, actionHandlers)
    override suspend fun markNotificationRead(itemId: Uuid) = delegate.markNotificationRead(itemId)
    override suspend fun sendPing(cookie: UInt) = delegate.sendPing(cookie)
    override suspend fun launchApp(uuid: Uuid) = delegate.launchApp(uuid)
    override suspend fun stopApp(uuid: Uuid) = delegate.stopApp(uuid)
    override fun doStuffAfterPermissionsGranted() = delegate.doStuffAfterPermissionsGranted()
    override fun checkForFirmwareUpdates() = delegate.checkForFirmwareUpdates()
    override suspend fun updateTimeIfNeeded() = delegate.updateTimeIfNeeded()
    override val bluetoothEnabled: StateFlow<BluetoothState> = MutableStateFlow(BluetoothState.Enabled)
    override val isScanningBle: StateFlow<Boolean> = MutableStateFlow(false)
    override val isScanningClassic: StateFlow<Boolean> = MutableStateFlow(false)
    override fun startBleScan() = delegate.startBleScan()
    override fun stopBleScan() = delegate.stopBleScan()
    override fun startClassicScan() = delegate.startClassicScan()
    override fun stopClassicScan() = delegate.stopClassicScan()
    override fun requestLockerSync(): Deferred<Unit> = delegate.requestLockerSync()
    override suspend fun sideloadApp(pbwPath: Path): Boolean = delegate.sideloadApp(pbwPath)
    override fun getAllLockerBasicInfo(): Flow<List<AppBasicProperties>> = delegate.getAllLockerBasicInfo()
    override fun getAllLockerUuids(): Flow<List<Uuid>> = delegate.getAllLockerUuids()
    override fun getLocker(type: AppType, searchQuery: String?, limit: Int): Flow<List<LockerWrapper>> = delegate.getLocker(type, searchQuery, limit)
    override fun getLockerApp(id: Uuid): Flow<LockerWrapper?> = delegate.getLockerApp(id)
    override suspend fun setAppOrder(id: Uuid, order: Int) = delegate.setAppOrder(id, order)
    override suspend fun waitUntilAppSyncedToWatch(id: Uuid, identifier: PebbleIdentifier, timeout: Duration): Boolean = delegate.waitUntilAppSyncedToWatch(id, identifier, timeout)
    override suspend fun removeApp(id: Uuid): Boolean = delegate.removeApp(id)
    override suspend fun addAppToLocker(app: LockerEntry) = delegate.addAppToLocker(app)
    override suspend fun addAppsToLocker(apps: List<LockerEntry>) = delegate.addAppsToLocker(apps)
    override fun restoreSystemAppOrder() = delegate.restoreSystemAppOrder()
    override val activeWatchface: StateFlow<LockerWrapper?> = MutableStateFlow(null)
    override fun notificationApps(): Flow<List<AppWithCount>> = delegate.notificationApps()
    override fun notificationAppChannelCounts(packageName: String): Flow<List<ChannelAndCount>> = delegate.notificationAppChannelCounts(packageName)
    override fun mostRecentNotificationsFor(pkg: String?, channelId: String?, contactId: String?, limit: Int): Flow<List<NotificationEntity>> = delegate.mostRecentNotificationsFor(pkg, channelId, contactId, limit)
    override fun mostRecentNotificationParticipants(limit: Int): Flow<List<String>> = delegate.mostRecentNotificationParticipants(limit)
    override fun updateNotificationAppMuteState(packageName: String?, muteState: MuteState) = delegate.updateNotificationAppMuteState(packageName, muteState)
    override fun updateNotificationAppState(packageName: String, vibePatternName: String?, colorName: String?, iconName: String?) = delegate.updateNotificationAppState(packageName, vibePatternName, colorName, iconName)
    override fun notificationRulesForApp(packageName: String): Flow<List<NotificationRuleEntity>> = delegate.notificationRulesForApp(packageName)
    override fun upsertNotificationRule(rule: NotificationRuleEntity) = delegate.upsertNotificationRule(rule)
    override fun deleteNotificationRule(rule: NotificationRuleEntity) = delegate.deleteNotificationRule(rule)
    override fun updateNotificationChannelMuteState(packageName: String, channelId: String, muteState: MuteState) = delegate.updateNotificationChannelMuteState(packageName, channelId, muteState)
    override fun updateNotificationAppAllowDuplicates(packageName: String, allowDuplicates: Boolean) = delegate.updateNotificationAppAllowDuplicates(packageName, allowDuplicates)
    override suspend fun getAppIcon(packageName: String): ImageBitmap? = delegate.getAppIcon(packageName)
    override val currentCall: MutableStateFlow<Call?> = MutableStateFlow(null)
    override fun calendars(): Flow<List<CalendarEntity>> = delegate.calendars()
    override fun updateCalendarEnabled(calendarId: Int, enabled: Boolean) = delegate.updateCalendarEnabled(calendarId, enabled)
    override fun otherPebbleCompanionAppsInstalled(): StateFlow<List<OtherPebbleApp>> = delegate.otherPebbleCompanionAppsInstalled()
    override suspend fun getAccountToken(appUuid: Uuid): String? = delegate.getAccountToken(appUuid)
    override val userFacingErrors: Flow<UserFacingError> = delegate.userFacingErrors
    override fun getContactsWithCounts(searchTerm: String, onlyNotified: Boolean): PagingSource<Int, ContactWithCount> = delegate.getContactsWithCounts(searchTerm, onlyNotified)
    override fun getContact(id: String): Flow<ContactWithCount?> = delegate.getContact(id)
    override fun updateContactState(contactId: String, muteState: MuteState, vibePatternName: String?) = delegate.updateContactState(contactId, muteState, vibePatternName)
    override suspend fun getContactImage(lookupKey: String): ImageBitmap? = delegate.getContactImage(lookupKey)
    override val analyticsEvents: Flow<AnalyticsEvent> = delegate.analyticsEvents
    override val healthSettings: Flow<HealthSettings> = delegate.healthSettings
    override fun updateHealthSettings(healthSettings: HealthSettings) = delegate.updateHealthSettings(healthSettings)
    override suspend fun getHealthDebugStats(): HealthDebugStats = delegate.getHealthDebugStats()
    override fun requestHealthData(fullSync: Boolean) = delegate.requestHealthData(fullSync)
    override fun sendHealthAveragesToWatch() = delegate.sendHealthAveragesToWatch()
    override val healthDataUpdated: SharedFlow<Unit> = MutableStateFlow(Unit)
    override suspend fun getCurrentPosition(maximumAge: Duration?, timeout: Duration?, highAccuracy: Boolean): GeolocationPositionResult = delegate.getCurrentPosition(maximumAge, timeout, highAccuracy)
    override suspend fun watchPosition(interval: Duration, highAccuracy: Boolean): Flow<GeolocationPositionResult> = delegate.watchPosition(interval, highAccuracy)
    override fun insertOrReplace(pin: TimelinePin) = delegate.insertOrReplace(pin)
    override fun delete(pinUuid: Uuid) = delegate.delete(pinUuid)
    override fun vibePatterns(): Flow<List<VibePattern>> = delegate.vibePatterns()
    override fun addCustomVibePattern(name: String, pattern: List<Long>) = delegate.addCustomVibePattern(name, pattern)
    override fun deleteCustomPattern(name: String) = delegate.deleteCustomPattern(name)
    override val watchPrefs: Flow<List<WatchPreference<*>>> = delegate.watchPrefs
    override fun setWatchPref(watchPref: WatchPreference<*>) = delegate.setWatchPref(watchPref)
    override fun updateWeatherData(weatherData: List<WeatherLocationData>) = delegate.updateWeatherData(weatherData)
    override suspend fun getLatestTimestamp(): Long? = delegate.getLatestTimestamp()
    override suspend fun getHealthDataAfter(afterTimestamp: Long): List<HealthDataEntity> = delegate.getHealthDataAfter(afterTimestamp)
    override suspend fun getOverlayEntriesAfter(afterTimestamp: Long, types: List<Int>): List<OverlayDataEntity> = delegate.getOverlayEntriesAfter(afterTimestamp, types)
    override suspend fun getHealthDataForRange(start: Long, end: Long) = delegate.getHealthDataForRange(start, end)
    override suspend fun getDailyAggregates(start: Long, end: Long) = delegate.getDailyAggregates(start, end)
    override suspend fun getTotalHealthData(start: Long, end: Long): HealthAggregates? = delegate.getTotalHealthData(start, end)
    override suspend fun getAverageHeartRate(start: Long, end: Long): Double? = delegate.getAverageHeartRate(start, end)
    override suspend fun getSleepEntries(start: Long, end: Long) = delegate.getSleepEntries(start, end)
    override suspend fun getDailySleepSession(dayStartEpochSec: Long): DailySleep? = delegate.getDailySleepSession(dayStartEpochSec)
    override suspend fun getLatestHeartRateReading(): LatestHeartRate? = delegate.getLatestHeartRateReading()
    override suspend fun getRestingHeartRate(dayStartEpochSec: Long): Int? = delegate.getRestingHeartRate(dayStartEpochSec)
    override suspend fun getHRZoneMinutes(start: Long, end: Long) = delegate.getHRZoneMinutes(start, end)
    override suspend fun getActivitySessions(start: Long, end: Long) = delegate.getActivitySessions(start, end)
    override suspend fun getTypicalSteps(dayOfWeek: Int) = delegate.getTypicalSteps(dayOfWeek)
    override suspend fun getTypicalSleepSeconds() = delegate.getTypicalSleepSeconds()
    override suspend fun populateDebugHealthData() = delegate.populateDebugHealthData()
}

fun configuredFakeWatch(
    connected: Boolean = true,
    watchType: WatchHardwarePlatform = WatchHardwarePlatform.CORE_ASTERIX,
    index: Int = 0,
): PebbleDevice {
    val name = fakeWatchDisplayName(watchType)
    val fakeIdentifier = "AA:BB:CC:DD:EE:${index.toString(16).padStart(2, '0').uppercase()}".asPebbleBleIdentifier()
    return if (connected) {
        FakeConnectedDevice(
            identifier = fakeIdentifier,
            firmwareUpdateAvailable = FirmwareUpdateCheckState(false, null),
            firmwareUpdateState = FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.Idle(),
            name = name,
            nickname = null,
            connectionFailureInfo = null,
            watchType = watchType,
            capabilities = setOf(ProtocolCapsFlag.SupportsBlobDbVersion),
        )
    } else {
        object : DiscoveredPebbleDevice {
            override val identifier = fakeIdentifier
            override val name: String = name
            override val nickname: String? = null
            override val connectionFailureInfo: ConnectionFailureInfo? = null

            override fun connect() {
            }
        }
    }
}

fun fakeWatchDisplayName(watchType: WatchHardwarePlatform): String = when (watchType) {
    WatchHardwarePlatform.UNKNOWN -> "Unknown (Basalt)"
    WatchHardwarePlatform.PEBBLE_ONE_EV_1 -> "Pebble One EV1 (Aplite)"
    WatchHardwarePlatform.PEBBLE_ONE_EV_2 -> "Pebble One EV2 (Aplite)"
    WatchHardwarePlatform.PEBBLE_ONE_EV_2_3 -> "Pebble One EV2.3 (Aplite)"
    WatchHardwarePlatform.PEBBLE_ONE_EV_2_4 -> "Pebble One EV2.4 (Aplite)"
    WatchHardwarePlatform.PEBBLE_ONE_POINT_FIVE -> "Pebble One V1.5 (Aplite)"
    WatchHardwarePlatform.PEBBLE_TWO_POINT_ZERO -> "Pebble Two V2.0 (Aplite)"
    WatchHardwarePlatform.PEBBLE_SNOWY_EVT_2 -> "Pebble Time EVT2 (Basalt)"
    WatchHardwarePlatform.PEBBLE_SNOWY_DVT -> "Pebble Time DVT (Basalt)"
    WatchHardwarePlatform.PEBBLE_BOBBY_SMILES -> "Pebble Time (Basalt)"
    WatchHardwarePlatform.PEBBLE_ONE_BIGBOARD_2 -> "Pebble One Bigboard 2 (Aplite)"
    WatchHardwarePlatform.PEBBLE_ONE_BIGBOARD -> "Pebble One Bigboard (Aplite)"
    WatchHardwarePlatform.PEBBLE_SNOWY_BIGBOARD -> "Pebble Time Bigboard (Basalt)"
    WatchHardwarePlatform.PEBBLE_SNOWY_BIGBOARD_2 -> "Pebble Time Bigboard 2 (Basalt)"
    WatchHardwarePlatform.PEBBLE_SPALDING_EVT -> "Pebble Time Round EVT (Chalk)"
    WatchHardwarePlatform.PEBBLE_SPALDING_PVT -> "Pebble Time Round (Chalk)"
    WatchHardwarePlatform.PEBBLE_SPALDING_BIGBOARD -> "Pebble Time Round Bigboard (Chalk)"
    WatchHardwarePlatform.PEBBLE_SILK_EVT -> "Pebble 2 EVT (Diorite)"
    WatchHardwarePlatform.PEBBLE_SILK -> "Pebble 2 (Diorite)"
    WatchHardwarePlatform.CORE_ASTERIX -> "Core Asterix (Flint)"
    WatchHardwarePlatform.CORE_OBELIX_EVT -> "Core Obelix EVT (Emery)"
    WatchHardwarePlatform.CORE_OBELIX_DVT -> "Core Obelix DVT (Emery)"
    WatchHardwarePlatform.CORE_OBELIX_PVT -> "Core Obelix (Emery)"
    WatchHardwarePlatform.CORE_GETAFIX_EVT -> "Core Getafix EVT (Gabbro)"
    WatchHardwarePlatform.CORE_GETAFIX_DVT -> "Core Getafix DVT (Gabbro)"
    WatchHardwarePlatform.CORE_GETAFIX_DVT2 -> "Core Getafix DVT2 (Gabbro)"
    WatchHardwarePlatform.PEBBLE_SILK_BIGBOARD -> "Pebble 2 Bigboard (Diorite)"
    WatchHardwarePlatform.PEBBLE_SILK_BIGBOARD_2_PLUS -> "Pebble 2 Bigboard 2+ (Diorite)"
    WatchHardwarePlatform.PEBBLE_ROBERT_EVT -> "Pebble 2+ EVT (Emery)"
    WatchHardwarePlatform.PEBBLE_ROBERT_BIGBOARD -> "Pebble 2+ Bigboard (Emery)"
    WatchHardwarePlatform.PEBBLE_ROBERT_BIGBOARD_2 -> "Pebble 2+ Bigboard 2 (Emery)"
    WatchHardwarePlatform.CORE_OBELIX_BIGBOARD -> "Core Obelix Bigboard (Emery)"
    WatchHardwarePlatform.CORE_OBELIX_BIGBOARD_2 -> "Core Obelix Bigboard 2 (Emery)"
}

fun isConsumerWatchPlatform(watchType: WatchHardwarePlatform): Boolean = when (watchType) {
    WatchHardwarePlatform.PEBBLE_BOBBY_SMILES,
    WatchHardwarePlatform.PEBBLE_SPALDING_PVT,
    WatchHardwarePlatform.PEBBLE_SILK,
    WatchHardwarePlatform.CORE_ASTERIX,
    WatchHardwarePlatform.CORE_OBELIX_PVT -> true
    else -> false
}
