package io.rebble.libpebblecommon.connection

import androidx.paging.PagingSource
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.ErrorTracker
import io.rebble.libpebblecommon.Housekeeping
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigHolder
import io.rebble.libpebblecommon.calendar.NewCalendarEvent
import io.rebble.libpebblecommon.calendar.PhoneCalendarSyncer
import io.rebble.libpebblecommon.calls.Call
import io.rebble.libpebblecommon.calls.LegacyPhoneReceiver
import io.rebble.libpebblecommon.calls.MissedCallSyncer
import io.rebble.libpebblecommon.connection.bt.BluetoothState
import io.rebble.libpebblecommon.connection.bt.BluetoothStateProvider
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattServerManager
import io.rebble.libpebblecommon.connection.bt.ble.transport.impl.configureKableCentral
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.ActionOverrides
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.CustomTimelineActionHandler
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.InternalActionHandler
import io.rebble.libpebblecommon.contacts.PhoneContactsSyncer
import io.rebble.libpebblecommon.database.dao.AppWithCount
import io.rebble.libpebblecommon.database.dao.ChannelAndCount
import io.rebble.libpebblecommon.database.dao.ContactWithCount
import io.rebble.libpebblecommon.database.dao.DailyMovementAggregate
import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.dao.TimelineNotificationRealDao
import io.rebble.libpebblecommon.database.dao.VibePatternDao
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationEntity
import io.rebble.libpebblecommon.database.entity.NotificationRuleEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.database.entity.TimelineNotification
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.di.initKoin
import io.rebble.libpebblecommon.health.Health
import io.rebble.libpebblecommon.health.HealthDebugStats
import io.rebble.libpebblecommon.health.HealthSettings
import io.rebble.libpebblecommon.image.PebbleBitmap
import io.rebble.libpebblecommon.js.InjectedPKJSHttpInterceptors
import io.rebble.libpebblecommon.js.JsTokenUtil
import io.rebble.libpebblecommon.locker.AppBasicProperties
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.Locker
import io.rebble.libpebblecommon.locker.LockerWrapper
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.notification.NotificationApi
import io.rebble.libpebblecommon.notification.NotificationListenerConnection
import io.rebble.libpebblecommon.notification.RespectDoNotDisturbMigrator
import io.rebble.libpebblecommon.notification.VibePattern
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.performPlatformSpecificInit
import io.rebble.libpebblecommon.plugin.LockerPluginLoader
import io.rebble.libpebblecommon.permissions.AppDataUsage
import io.rebble.libpebblecommon.permissions.AppDataUsageApi
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.AppPermissionsApi
import io.rebble.libpebblecommon.permissions.PermissionDecisionRecord
import io.rebble.libpebblecommon.permissions.AppPermissionStatus
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.permissions.RequestedPermissions
import io.rebble.libpebblecommon.plugin.ConfigMessageTarget
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.PluginActionLog
import io.rebble.libpebblecommon.plugin.PluginActionLogApi
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginClient
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.PluginOAuthApi
import io.rebble.libpebblecommon.plugin.SourceUpdate
import io.rebble.libpebblecommon.plugin.files.PluginFileAccess
import io.rebble.libpebblecommon.plugin.files.PluginFileAccessApi
import io.rebble.libpebblecommon.services.DailySleep
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.time.TimeChanged
import io.rebble.libpebblecommon.util.SystemGeolocation
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.weather.WeatherLocationData
import io.rebble.libpebblecommon.weather.WeatherManager
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
import org.koin.core.Koin
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal data class PhoneCapabilities internal constructor(val capabilities: Set<ProtocolCapsFlag>)
internal data class PlatformFlags(val flags: UInt)

public sealed class PebbleConnectionEvent {
    public data class PebbleConnectedEvent internal constructor(val device: CommonConnectedDevice) : PebbleConnectionEvent()
    public data class PebbleDisconnectedEvent internal constructor(val identifier: PebbleIdentifier) : PebbleConnectionEvent()
}

public interface LibPebble : Scanning, RequestSync, LockerApi, NotificationApps, CallManagement, Calendar,
    OtherPebbleApps, PKJSToken, Watches, Errors, Contacts, AnalyticsEvents, HealthApi, WatchPrefs,
    SystemGeolocation, Timeline, Vibrations, Weather, HealthDataApi, Plugins, AppPermissionsApi,
    PluginFileAccessApi, NotificationReplies, AppDataUsageApi, PluginActionLogApi {
    public fun init()

    public val config: StateFlow<LibPebbleConfig>
    public fun updateConfig(config: LibPebbleConfig)

    // Generally, use these. They will act on all watches (or all connected watches, if that makes
    // sense)
    public suspend fun sendNotification(notification: TimelineNotification, actionHandlers: Map<UByte, CustomTimelineActionHandler>? = null)
    public suspend fun markNotificationRead(itemId: Uuid)
    public suspend fun sendPing(cookie: UInt)
    public suspend fun launchApp(uuid: Uuid)
    public suspend fun stopApp(uuid: Uuid)
    // ....

    public fun doStuffAfterPermissionsGranted()
    public fun checkForFirmwareUpdates(force: Boolean)
    public suspend fun updateTimeIfNeeded()
}

public sealed class UserFacingError {
    public abstract val message: String

    public data class FailedToDownloadPbw internal constructor(override val message: String) : UserFacingError()
    public data class FailedToRemovePbwFromLocker internal constructor(override val message: String) : UserFacingError()
    public data class FailedToSideloadApp internal constructor(override val message: String) : UserFacingError()
    public data class FailedToScan internal constructor(override val message: String) : UserFacingError()
    public data class FailedToInstallLanguagePack internal constructor(override val message: String) : UserFacingError()
    public data class MissingCompanionApp internal constructor(
        override val message: String,
        val appName: String,
        val appUuid: Uuid,
        val downloadUrl: String?
    ) : UserFacingError()
}

public data class OtherPebbleApp internal constructor(
    val pkg: String,
    val name: String,
)

public interface HealthApi {
    public val healthSettings: Flow<HealthSettings>
    public fun updateHealthSettings(healthSettings: HealthSettings)
    public suspend fun updateImperialUnits(imperialUnits: Boolean)
    public suspend fun getHealthDebugStats(): HealthDebugStats
    public fun requestHealthData(fullSync: Boolean = false)
    public fun sendHealthAveragesToWatch()
    public val healthDataUpdated: SharedFlow<Unit>
}

/**
 * Represents the most recent heart rate reading from the watch.
 */
public data class LatestHeartRate internal constructor(
    val bpm: Int,
    val timestampEpochSec: Long,
)

public interface HealthDataApi {
    public suspend fun getLatestTimestamp(): Long?
    public suspend fun getHealthDataAfter(afterTimestamp: Long): List<HealthDataEntity>
    public suspend fun getOverlayEntriesAfter(afterTimestamp: Long, types: List<Int>): List<OverlayDataEntity>

    /** Returns minute-level health data for the given epoch-second range. */
    public suspend fun getHealthDataForRange(start: Long, end: Long): List<HealthDataEntity>

    /** Returns per-day step/calorie/distance/active-minute aggregates for the range. */
    public suspend fun getDailyAggregates(start: Long, end: Long): List<DailyMovementAggregate>

    /** Returns a single aggregate (total steps, calories, distance, active minutes) for the entire range. */
    public suspend fun getTotalHealthData(start: Long, end: Long): HealthAggregates?

    /** Returns the average heart rate (BPM) for non-zero readings in the range, or null if no data. */
    public suspend fun getAverageHeartRate(start: Long, end: Long): Double?

    /** Returns sleep overlay entries (Sleep + DeepSleep types) in the range. */
    public suspend fun getSleepEntries(start: Long, end: Long): List<OverlayDataEntity>

    /** Returns all sleep sessions for a given day plus aggregate totals, using the 6PM-2PM search window.
     *  @param dayStartEpochSec start-of-day in epoch seconds (midnight local time) */
    public suspend fun getDailySleepSession(dayStartEpochSec: Long): DailySleep?

    /** Returns the most recent non-zero heart rate reading, or null if none exists. */
    public suspend fun getLatestHeartRateReading(): LatestHeartRate?

    /**
     * Computes the resting heart rate (BPM) for the given day from sleep + HR samples. Returns
     * null when there's no sleep session or not enough HR samples within it.
     *
     * @param dayStartEpochSec start-of-day in epoch seconds (midnight local time)
     */
    public suspend fun getRestingHeartRate(dayStartEpochSec: Long): Int?

    /** Returns minutes spent in each heart rate zone (keys: 0=rest, 1=light, 2=cardio, 3=high). */
    public suspend fun getHRZoneMinutes(start: Long, end: Long): Map<Int, Long>

    /** Returns activity overlay entries (Walk, Run, OpenWorkout types) in the range. */
    public suspend fun getActivitySessions(start: Long, end: Long): List<OverlayDataEntity>

    /** Returns hourly typical (historical average) step counts for the given weekday.
     *  @param dayOfWeek kotlinx.datetime.DayOfWeek ordinal (0=Monday, 6=Sunday) */
    public suspend fun getTypicalSteps(dayOfWeek: Int): List<Long>

    /** Returns the 30-day average sleep duration in seconds. */
    public suspend fun getTypicalSleepSeconds(): Long

    /** Wipes all health data and populates 30 days of fake data for testing. */
    public suspend fun populateDebugHealthData()
}

public interface Weather {
    public fun updateWeatherData(weatherData: List<WeatherLocationData>)

    /** Latest stored weather, one entry per saved location. Temperatures are already in the
     *  user's configured unit — whoever wrote them converted first. */
    public val currentWeather: Flow<List<WeatherAppEntry>>
}

/** The installed plugins, and the only way to call them (see `new-plugin-api.md`). */
public interface Plugins {
    /** For plugins outside libpebble3, whose Koin context is isolated. */
    public fun registerPlugin(plugin: NativePlugin)

    /** Removes a plugin added with [registerPlugin]; a built-in disappears from every listing. */
    public fun unregisterPlugin(pluginUuid: Uuid)

    /** Built-in plugins (phone-side data sources like Music or Weather), not backed by a pbw. */
    public fun nativePlugins(): Flow<List<PluginMetadata>>

    public val disabledBuiltInPlugins: StateFlow<Set<Uuid>>

    /** A disabled built-in is hidden from every caller, as if it weren't installed. */
    public fun setBuiltInPluginEnabled(pluginUuid: Uuid, enabled: Boolean)

    /** The user's choice of plugin for each source several plugins serve, keyed by `category/item`. */
    public val preferredSourcePlugins: StateFlow<Map<String, Uuid>>

    /** Null goes back to the default order. */
    public fun setPreferredSourcePlugin(category: String, item: String, pluginUuid: Uuid?)

    /** The plugin that answers a read of [category]/[item] that doesn't name one. */
    public fun sourcePlugin(category: String, item: String): PluginMetadata?

    /** The script behind [pluginUuid]'s settings page, or null if it has none. */
    public fun configMessageTarget(pluginUuid: String): ConfigMessageTarget?

    public fun getAllPlugins(): List<PluginMetadata>

    public fun observe(
        caller: PluginCaller,
        plugin: PluginMetadata,
        category: String,
        item: String,
        properties: List<String>? = null,
        iconPixelSize: IconPixelSize? = null,
        limit: Int? = null,
    ): Flow<SourceUpdate>

    /** The first update, or a [PluginErrors.TIMEOUT] error. */
    public suspend fun read(
        caller: PluginCaller,
        plugin: PluginMetadata,
        category: String,
        item: String,
        properties: List<String>? = null,
        timeout: Duration = DEFAULT_READ_TIMEOUT,
    ): SourceUpdate

    public suspend fun invoke(
        caller: PluginCaller,
        plugin: PluginMetadata,
        action: String,
        args: JsonObject,
        /** Null leaves it to the plugin: a JS plugin's request times out on its own. */
        timeout: Duration? = null,
    ): ActionResult

    public companion object {
        public val DEFAULT_READ_TIMEOUT: Duration = 10.seconds
    }
}

public interface NotificationReplies {
    public suspend fun replyToDisplayedNotification(text: String, at: Instant): DisplayedNotificationReplyResult
}

public sealed interface DisplayedNotificationReplyResult {
    public data class Sent(val title: String?) : DisplayedNotificationReplyResult
    public data object NoNotificationDisplayed : DisplayedNotificationReplyResult
    public data object NotReplyable : DisplayedNotificationReplyResult
    public data object Failed : DisplayedNotificationReplyResult
    public data object Unsupported : DisplayedNotificationReplyResult
}

public interface Timeline {
    public fun insertOrReplace(pin: TimelinePin)
    public fun delete(pinUuid: Uuid)
    public suspend fun insertOrReplace(reminder: TimelineReminder)
    public suspend fun deleteReminder(reminderUuid: Uuid)

    /**
     * Handles notification and reminder actions with this `internalType`. The action is stored
     * with its item, so this works for items sent before an app restart.
     */
    public fun setActionHandler(internalType: String, handler: InternalActionHandler)
}

public interface Errors {
    /**
     * Errors which should be displayed to the user (e.g. using a snackbar).
     */
    public val userFacingErrors: Flow<UserFacingError>
}

public data class AnalyticsEvent internal constructor(
    val name: String,
    val parameters: Map<String, String>,
)

public interface AnalyticsEvents {
    public val analyticsEvents: Flow<AnalyticsEvent>
    public val heartbeatCounterIncrements: Flow<String>
}

public interface WatchPrefs {
    public val watchPrefs: Flow<List<WatchPreference<*>>>
    public fun setWatchPref(watchPref: WatchPreference<*>)
}

public interface Watches {
    /** Doesn't emit until known watches have been loaded. */
    public val watches: Flow<List<PebbleDevice>>
    public val connectionEvents: Flow<PebbleConnectionEvent>
    public fun watchesDebugState(): String
}

public interface WebServices {
    public suspend fun fetchLocker(): LockerModelWrapper?
    public suspend fun removeFromLocker(id: Uuid): Boolean
    public suspend fun checkForFirmwareUpdate(watch: WatchInfo, force: Boolean): FirmwareUpdateCheckResult
    public fun uploadMemfaultChunk(chunk: ByteArray, watchInfo: WatchInfo)
    public fun uploadAnalyticsHeartbeat(payload: ByteArray, watchInfo: WatchInfo)
}

public interface TokenProvider {
    public suspend fun getDevToken(): String?
}

public data class FirmwareUpdateCheckState internal constructor(
    val checkingForUpdates: Boolean,
    val result: FirmwareUpdateCheckResult?,
)

public sealed class FirmwareUpdateCheckResult {
    public data class FoundUpdate(
        val version: FirmwareVersion,
        val url: String,
        val notes: String,
        /** Whether the watch boots this after a normal install, rather than going through recovery. */
        val installsDirectly: Boolean,
    ) : FirmwareUpdateCheckResult()

    public data object FoundNoUpdate : FirmwareUpdateCheckResult()

    public data class UpdateCheckFailed(
        val error: String,
    ) : FirmwareUpdateCheckResult()
}

public interface Calendar {
    public fun calendars(): Flow<List<CalendarEntity>>
    public fun updateCalendarEnabled(calendarId: Int, enabled: Boolean)
    public suspend fun createEvent(calendarId: Int, event: NewCalendarEvent): String?
    /** The synced calendar matching the platform's default for new events, if known. */
    public suspend fun defaultCalendar(): CalendarEntity?
}

public fun Flow<List<PebbleDevice>>.forDevice(identifier: String): Flow<PebbleDevice> {
    return mapNotNull { it.firstOrNull { it.identifier.asString == identifier } }
}

public interface Scanning {
    public val bluetoothEnabled: StateFlow<BluetoothState>
    public val isScanningBle: StateFlow<Boolean>
    public val isScanningClassic: StateFlow<Boolean>
    public fun startBleScan()
    public fun stopBleScan()
    public fun startClassicScan()
    public fun stopClassicScan()

    /** Adds a watch reached over a TCP socket at [address] ("host:port"), bypassing any scan. */
    public fun addQemuWatch(address: String, connect: Boolean = false)
}

public interface RequestSync {
    public fun requestLockerSync(): Deferred<Unit>
}

public interface LockerApi {
    /**
     * @return true if the app was successfully synced and launched on all connected watches.
     */
    /**
     * [loadOnWatch] launches it once it has synced; leave it off for background installs.
     * [granted]: the answers to [reviewSideload].
     */
    public suspend fun sideloadApp(pbwPath: Path, loadOnWatch: Boolean = true, granted: Map<String, Boolean?> = emptyMap()): Boolean
    /** Null if there's nothing to ask, or the pbw can't be read. */
    public suspend fun reviewSideload(pbwPath: Path): InstallReview?
    public suspend fun reviewInstall(appUuid: Uuid, appName: String, requested: RequestedPermissions): InstallReview?
    public fun getAllLockerBasicInfo(): Flow<List<AppBasicProperties>>
    public fun getAllLockerUuids(): Flow<List<Uuid>>
    public fun getLocker(type: AppType, searchQuery: String?, limit: Int): Flow<List<LockerWrapper>>
    public fun getLockerApp(id: Uuid): Flow<LockerWrapper?>
    /**
     * [id]'s permissions, or every installed app's if [id] is null. For one app, null until its pbw
     * has been fetched, so what it uses isn't known yet.
     */
    public fun appPermissionStatuses(id: Uuid?): Flow<List<AppPermissionStatus>?>
    /**
     * The config page URL for an installed app, from its appinfo `configPage` — an http(s) URL as
     * given, or an HTML file bundled in the pbw returned as a data URL. Null when the app declares
     * none, so callers fall back to the PKJS `showConfiguration` flow.
     */
    public suspend fun appConfigPageUrl(uuid: Uuid): String?
    /** Whether [uuid]'s config page may load [url], checked like the app's own requests. */
    public suspend fun mayLoadInConfigPage(uuid: Uuid, url: String): Boolean
    /** The domains [uuid]'s config page may connect to, each covering its subdomains; null for any. */
    public suspend fun configPageDomains(uuid: Uuid): List<String>?
    /** A text file from an installed app's pbw, or null if the app or file is missing. */
    public suspend fun getAppFile(uuid: Uuid, name: String): String?
    public suspend fun setAppOrder(id: Uuid, order: Int)
    public suspend fun waitUntilAppSyncedToWatch(id: Uuid, identifier: PebbleIdentifier, timeout: Duration): Boolean
    /** Also erases everything the app stored, on this phone and on connected watches. */
    public suspend fun removeApp(id: Uuid): Boolean
    public suspend fun addAppToLocker(app: LockerEntry, granted: Map<String, Boolean?> = emptyMap())
    /** [permissions]: the user's decisions for these apps from other devices or the web store. */
    public suspend fun addAppsToLocker(
        apps: List<LockerEntry>,
        permissions: Map<Uuid, Map<String, PermissionDecisionRecord>>,
    )
    /** Lets the next locker sync replace a sideloaded app with its copy in the user's locker. */
    public suspend fun clearSideloaded(id: Uuid)
    public fun restoreSystemAppOrder()
    public val activeWatchface: StateFlow<LockerWrapper?>
}

public interface Contacts {
    public fun getContactsWithCounts(searchTerm: String, onlyNotified: Boolean): PagingSource<Int, ContactWithCount>
    public fun getContact(id: String): Flow<ContactWithCount?>
    public fun updateContactState(contactId: String, muteState: MuteState, vibePatternName: String?)
    public suspend fun getContactImage(lookupKey: String): PebbleBitmap?
}

public interface NotificationApps {
    public fun notificationApps(): Flow<List<AppWithCount>>
    public fun notificationAppChannelCounts(packageName: String): Flow<List<ChannelAndCount>>
    public fun mostRecentNotificationsFor(pkg: String?, channelId: String?, contactId: String?, limit: Int): Flow<List<NotificationEntity>>
    public fun mostRecentNotificationParticipants(limit: Int): Flow<List<String>>

    /**
     * Update mute state of the specified app. Updates all apps if [packageName] is null.
     */
    public fun updateNotificationAppMuteState(packageName: String?, muteState: MuteState)
    public fun updateNotificationAppState(
        packageName: String,
        vibePatternName: String?,
        colorName: String?,
        iconCode: String?,
    )
    public fun updateNotificationChannelMuteState(
        packageName: String,
        channelId: String,
        muteState: MuteState,
    )
    public fun updateNotificationAppAllowDuplicates(packageName: String, allowDuplicates: Boolean)
    public fun updateNotificationAppSendImages(packageName: String, sendImages: Boolean)

    public fun notificationRulesForApp(packageName: String): Flow<List<NotificationRuleEntity>>
    public fun upsertNotificationRule(rule: NotificationRuleEntity)
    public fun deleteNotificationRule(rule: NotificationRuleEntity)

    /** Will only return a value on Android */
    public suspend fun getAppIcon(packageName: String): PebbleBitmap?
}

public interface Vibrations {
    public fun vibePatterns(): Flow<List<VibePattern>>
    public fun addCustomVibePattern(name: String, pattern: List<Long>)
    public fun deleteCustomPattern(name: String)
}

public interface OtherPebbleApps {
    /** Any other companion apps installed will likely break connecitivity (multiple PPoG services) */
    public fun otherPebbleCompanionAppsInstalled(): StateFlow<List<OtherPebbleApp>>
}

public interface CallManagement {
    public val currentCall: MutableStateFlow<Call?>
}

public interface PKJSToken {
    public suspend fun getAccountToken(appUuid: Uuid): String?
}

// Impl

public class LibPebble3 internal constructor(
    private val watchManager: WatchManager,
    private val scanning: Scanning,
    private val locker: Locker,
    private val lockerPluginLoader: LockerPluginLoader,
    private val timeChanged: TimeChanged,
    private val webSyncManager: RequestSync,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val gattServerManager: GattServerManager,
    private val bluetoothStateProvider: BluetoothStateProvider,
    private val notificationListenerConnection: NotificationListenerConnection,
    private val notificationApi: NotificationApi,
    private val timelineNotificationsDao: TimelineNotificationRealDao,
    private val actionOverrides: ActionOverrides,
    private val phoneCalendarSyncer: PhoneCalendarSyncer,
    private val missedCallSyncer: MissedCallSyncer,
    private val libPebbleConfigFlow: LibPebbleConfigHolder,
    private val health: Health,
    private val otherPebbleApps: OtherPebbleApps,
    private val jsTokenUtil: JsTokenUtil,
    private val housekeeping: Housekeeping,
    private val errorTracker: ErrorTracker,
    private val phoneContactsSyncer: PhoneContactsSyncer,
    private val contacts: Contacts,
    private val analytics: AnalyticsEvents,
    private val systemGeolocation: SystemGeolocation,
    private val timeline: Timeline,
    private val legacyPhoneReceiver: LegacyPhoneReceiver,
    private val vibePatternDao: VibePatternDao,
    private val watchPreferences: WatchPrefs,
    private val weatherManager: WeatherManager,
    private val appPermissions: AppPermissions,
    private val appDataUsage: AppDataUsage,
    pluginActionLog: PluginActionLog,
    pluginClient: PluginClient,
    pluginFileAccess: PluginFileAccess,
    private val notificationReplies: NotificationReplies,
    private val respectDoNotDisturbMigrator: RespectDoNotDisturbMigrator,
) : LibPebble, Scanning by scanning, RequestSync by webSyncManager, LockerApi by locker,
    NotificationApps by notificationApi, Calendar by phoneCalendarSyncer,
    OtherPebbleApps by otherPebbleApps, PKJSToken by jsTokenUtil, Watches by watchManager,
    Errors by errorTracker, Contacts by contacts, AnalyticsEvents by analytics,
    HealthApi by health, SystemGeolocation by systemGeolocation, Timeline by timeline,
    Vibrations by notificationApi, WatchPrefs by watchPreferences, Weather by weatherManager,
    HealthDataApi by health, Plugins by pluginClient,
    AppPermissionsApi by appPermissions, PluginFileAccessApi by pluginFileAccess,
    NotificationReplies by notificationReplies, AppDataUsageApi by appDataUsage,
    PluginActionLogApi by pluginActionLog {
    private val logger = Logger.withTag("LibPebble3")
    private val initialized = AtomicBoolean(false)

    override fun init() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            logger.w { "Already initialized!!!" }
            return
        }
        appPermissions.init()
        appDataUsage.init()
        libPebbleCoroutineScope.launch { respectDoNotDisturbMigrator.migrateIfNeeded() }
        WatchHardwarePlatform.init(libPebbleCoroutineScope, libPebbleConfigFlow.config)
        bluetoothStateProvider.init()
        gattServerManager.init()
        watchManager.init()
        phoneCalendarSyncer.init()
        phoneContactsSyncer.init()
        missedCallSyncer.init()
        notificationListenerConnection.init(this)
        notificationApi.init()
        timeChanged.registerForTimeChanges {
            logger.d("Time changed")
            libPebbleCoroutineScope.launch { forEachConnectedWatch { updateTime() } }
        }
        housekeeping.init()
        health.init()
        legacyPhoneReceiver.init(currentCall)
        libPebbleCoroutineScope.launch {
            vibePatternDao.ensureAllDefaultsInserted()
        }
        locker.init(this)
        lockerPluginLoader.init()

        performPlatformSpecificInit()
    }

    override val config: StateFlow<LibPebbleConfig> = libPebbleConfigFlow.config

    override fun updateConfig(config: LibPebbleConfig) {
        logger.d("Updated config: $config")
        libPebbleConfigFlow.update(config)
    }

    override val currentCall: MutableStateFlow<Call?> = MutableStateFlow(null)

    override suspend fun sendNotification(notification: TimelineNotification, actionHandlers: Map<UByte, CustomTimelineActionHandler>?) {
        timelineNotificationsDao.insertOrReplace(notification)
        actionHandlers?.let { actionOverrides.setActionHandlers(notification.itemId, actionHandlers) }
    }

    override suspend fun markNotificationRead(itemId: Uuid) {
        timelineNotificationsDao.markNotificationRead(itemId)
        actionOverrides.setActionHandlers(itemId, emptyMap())
    }

    override suspend fun sendPing(cookie: UInt) {
        forEachConnectedWatch { sendPing(cookie) }
    }

    override suspend fun launchApp(uuid: Uuid) {
        locker.maybeSetActiveWatchface(uuid, onlyIfNotAlreadySet = false)
        forEachConnectedWatch { launchApp(uuid) }
    }

    override suspend fun stopApp(uuid: Uuid) {
        forEachConnectedWatch { stopApp(uuid) }
    }

    override fun doStuffAfterPermissionsGranted() {
        phoneCalendarSyncer.handlePermissionsGranted()
        missedCallSyncer.init()
        phoneContactsSyncer.init()
        watchManager.seedBondedWatchesIfNeeded()
    }

    override fun checkForFirmwareUpdates(force: Boolean) {
        forEachConnectedWatchInAnyState { checkforFirmwareUpdate(force) }
    }

    override suspend fun updateTimeIfNeeded() {
        forEachConnectedWatch { updateTimeIfNeeded() }
    }

    private suspend fun forEachConnectedWatch(block: suspend ConnectedPebbleDevice.() -> Unit) {
        watches.first().filterIsInstance<ConnectedPebbleDevice>().forEach {
            it.block()
        }
    }

    private fun forEachConnectedWatchInAnyState(block: CommonConnectedDevice.() -> Unit) {
        libPebbleCoroutineScope.launch {
            watches.first().filterIsInstance<CommonConnectedDevice>().forEach {
                it.block()
            }
        }
    }

    public companion object {
        private lateinit var koin: Koin

        internal val isCreated: Boolean get() = ::koin.isInitialized

        public fun create(
            /**
             * Default config, before any changes are made.
             *
             * Config will be persisted - this parameter will only be used on the first init.
             */
            defaultConfig: LibPebbleConfig,
            webServices: WebServices,
            appContext: AppContext,
            tokenProvider: TokenProvider,
            proxyTokenProvider: StateFlow<String?>,
            transcriptionProvider: TranscriptionProvider,
            injectedPKJSHttpInterceptors: InjectedPKJSHttpInterceptors = InjectedPKJSHttpInterceptors(emptyList()),
            pluginOAuthApi: PluginOAuthApi? = null,
        ): LibPebble {
            koin = initKoin(defaultConfig, webServices, appContext, tokenProvider, proxyTokenProvider, transcriptionProvider, injectedPKJSHttpInterceptors, pluginOAuthApi)
            // Reads the persisted config (not defaultConfig), but must still precede anything that
            // touches Kable's shared central manager. Every Koin single here is lazy, and Kable is
            // only reached on the first scan/connect, which happens after create() returns.
            configureKableCentral(
                koin.get<LibPebbleConfigHolder>().config.value.bleConfig.centralStateRestoration
            )
            val libPebble = koin.get<LibPebble>()
            return libPebble
        }
    }
}
