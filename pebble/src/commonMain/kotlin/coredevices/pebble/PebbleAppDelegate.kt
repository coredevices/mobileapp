package coredevices.pebble

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.analytics.CoreAnalytics
import coredevices.analytics.heartbeatWatchConnectGoalName
import coredevices.analytics.heartbeatWatchConnectedName
import coredevices.database.WeatherLocationDao
import coredevices.database.insertDefaultWeatherLocationOnce
import coredevices.pebble.firmware.FirmwareUpdateUiTracker
import coredevices.pebble.services.AppstoreSourceInitializer
import coredevices.util.AppResumed
import coredevices.util.DoneInitialOnboarding
import coredevices.util.PermissionRequester
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import io.rebble.libpebblecommon.connection.BleDiscoveredPebbleDevice
import io.rebble.libpebblecommon.connection.CommonConnectedDevice
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectedPebbleDeviceInRecovery
import io.rebble.libpebblecommon.connection.ConnectingPebbleDevice
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlin.time.Clock

class PebbleAppDelegate(
    private val libPebble: LibPebble,
    private val firmwareUpdateUiTracker: FirmwareUpdateUiTracker,
    private val permissionsRequester: PermissionRequester,
    private val appResumed: AppResumed,
    private val doneInitialOnboarding: DoneInitialOnboarding,
    private val analytics: CoreAnalytics,
    private val clock: Clock,
    private val weatherLocationDao: WeatherLocationDao,
    private val settings: Settings,
    private val appstoreSourceInitializer: AppstoreSourceInitializer,
) {
    private val logger = Logger.withTag("PebbleAppDelegate")

    fun init() {
        logger.d { "init()" }
        permissionsRequester.init()
        GlobalScope.launch {
            appstoreSourceInitializer.initAppstoreSourcesDB()
            weatherLocationDao.insertDefaultWeatherLocationOnce(settings)
            // Don't initialize everything if the user just started the app for the first time and
            // hasn't gone through onboarding yet - it would create permission prompts on ios (we
            // want to control when those are shown).
            doneInitialOnboarding.doneInitialOnboarding.await()
            logger.d { "actually initializing libpebble.." }
            libPebble.init()
            GlobalScope.launch {
                appResumed.appResumed.collect {
                    libPebble.doStuffAfterPermissionsGranted()
                }
            }
            GlobalScope.launch {
                libPebble.analyticsEvents.collect {
                    analytics.logEvent(it.name, it.parameters)
                }
            }
            GlobalScope.launch {
                libPebble.watches.mapNotNull { watches ->
                    watches.maxByOrNull { if (it is KnownPebbleDevice) it.lastConnected.epochSeconds else 0 }
                }.filterIsInstance<KnownPebbleDevice>().distinctUntilChanged().collect { device ->
                    Firebase.crashlytics.setCustomKey("last_connected_serial", device.serial)
                    Firebase.crashlytics.setCustomKey(
                        "last_connected_watch_type",
                        device.watchType.revision
                    )
                    Firebase.crashlytics.setCustomKey(
                        "last_connected_watch_firmware",
                        device.runningFwVersion
                    )
                }
            }
            GlobalScope.launch {
                libPebble.watches.collect { watches ->
                    watches.forEach { watch ->
                        if (watch is ConnectedPebble.Firmware) {
                            watch.firmwareUpdateAvailable?.let { fwup ->
                                if (fwup is FirmwareUpdateCheckResult.FoundUpdate) {
                                    firmwareUpdateUiTracker.maybeNotifyFirmwareUpdate(
                                        fwup,
                                        watch.identifier,
                                        watch.name,
                                    )
                                }
                            }
                        }
                        if (watch is ConnectedPebble.Firmware && watch.firmwareUpdateState is FirmwareUpdater.FirmwareUpdateStatus.InProgress) {
                            firmwareUpdateUiTracker.firmwareUpdateIsInProgress(watch.identifier)
                        }
                    }
                    watches.groupBy { it.watchType() }.forEach { (watchType, watches) ->
                        if (watchType == null) {
                            return@forEach
                        }
                        val connected = watches.any { it.isConnected() }
                        analytics.logHeartbeatState(
                            heartbeatWatchConnectedName(watchType),
                            connected,
                            clock.now(),
                        )
                        val connectGoal = watches.any { it.hasConnectGoal() }
                        analytics.logHeartbeatState(
                            heartbeatWatchConnectGoalName(watchType),
                            connectGoal,
                            clock.now(),
                        )
                    }
                }
            }
            GlobalScope.launch {
                libPebble.watches.flatMapLatest { watches ->
                    val flows = watches.filterIsInstance<ConnectedPebble.PKJS>()
                        .map { it.currentPKJSSession }
                    combine(flows) { it.toList() }
                }.collect {
                    val activeSessions = it.filterNotNull()
                    Firebase.crashlytics.setCustomKey("active_pkjs_sessions", activeSessions.size)
                    logger.d { "Active PKJS sessions: ${activeSessions.size}" }
                    activeSessions.take(4).forEachIndexed { index, session ->
                        val uuid = session.uuid.toString()
                        Firebase.crashlytics.setCustomKey(
                            "pkjs_session_${index}_app_uuid",
                            uuid
                        )
                        Firebase.crashlytics.setCustomKey(
                            "pkjs_${uuid}_app",
                            session.appInfo.longName
                        )
                        Firebase.crashlytics.setCustomKey(
                            "pkjs_${uuid}_ready",
                            session.sessionIsReady
                        )
                        logger.d { "PKJS session ${index}: $uuid (${session.appInfo.longName}) is ready: ${session.sessionIsReady}" }
                    }
                }

            }
            GlobalScope.launch {
                libPebble.watches.collect { watches ->
                    val connected = watches.filterIsInstance<CommonConnectedDevice>().firstOrNull()
                    val lastConnectedSerial = if (connected != null) {
                        connected.serial
                    } else {
                        watches.filterIsInstance<KnownPebbleDevice>()
                            .maxByOrNull { it.lastConnected }?.serial
                    }
                    analytics.updateLastConnectedSerial(lastConnectedSerial)
                }
            }
        }
    }

    suspend fun getWatchLogs(): Path? {
        return libPebble.watches.value.filterIsInstance<ConnectedPebble.Logs>().firstOrNull()
            ?.gatherLogs()
    }

    suspend fun getCoreDump(): Path? {
        return libPebble.watches.value.filterIsInstance<ConnectedPebble.CoreDump>().firstOrNull()
            ?.getCoreDump(unread = false)
    }

    fun getPKJSSessions(): String {
        val sessions = libPebble.watches.value.filterIsInstance<ConnectedPebble.PKJS>()
            .mapNotNull { it.currentPKJSSession.value }
        return buildString {
            sessions.forEach {
                appendLine("PKJS Session: ${it.uuid}")
                appendLine("  App: ${it.appInfo.longName} (${it.appInfo.shortName})")
                appendLine("  Ready: ${it.sessionIsReady}")
                appendLine("  Device: ${it.device.watchInfo.serial} ${it.device.watchInfo.platform}")
                appendLine()
            }
        }
    }

    fun onAppResumed() {
        logger.d { "onAppResumed" }
        appResumed.onAppResumed()
    }

    suspend fun performBackgroundWork() {
        val jobs = listOf(
            GlobalScope.launch {
                // TODO not suspending
                libPebble.checkForFirmwareUpdates()
            },
            GlobalScope.launch {
                libPebble.requestLockerSync().await()
            },
        )
        jobs.joinAll()
    }
}

fun PebbleDevice.watchType(): WatchHardwarePlatform? = when (this) {
    is KnownPebbleDevice -> watchType
    is BleDiscoveredPebbleDevice -> pebbleScanRecord.extendedInfo?.hardwarePlatform?.toUByte()
        ?.let {
            WatchHardwarePlatform.fromProtocolNumber(it)
        }

    else -> null
}

fun PebbleDevice.isConnected(): Boolean =
    this is ConnectedPebbleDevice || this is ConnectedPebbleDeviceInRecovery

fun PebbleDevice.hasConnectGoal(): Boolean = isConnected() || this is ConnectingPebbleDevice