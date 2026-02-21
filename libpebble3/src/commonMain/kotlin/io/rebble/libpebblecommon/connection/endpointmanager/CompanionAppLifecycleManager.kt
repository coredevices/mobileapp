package io.rebble.libpebblecommon.connection.endpointmanager

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.connection.CompanionApp
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.js.CompanionAppDevice
import io.rebble.libpebblecommon.js.PKJSApp
import io.rebble.libpebblecommon.locker.Locker
import io.rebble.libpebblecommon.locker.LockerPBWCache
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.services.app.AppRunStateService
import io.rebble.libpebblecommon.services.appmessage.AppMessageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

class CompanionAppLifecycleManager(
    private val lockerPBWCache: LockerPBWCache,
    private val lockerEntryDao: LockerEntryRealDao,
    private val appRunStateService: AppRunStateService,
    private val appMessagesService: AppMessageService,
    private val locker: Locker,
    private val scope: ConnectionCoroutineScope,
    private val libPebbleConfigFlow: LibPebbleConfigFlow,
): ConnectedPebble.PKJS, ConnectedPebble.CompanionAppControl {
    companion object {
        private val logger = Logger.withTag(CompanionAppLifecycleManager::class.simpleName!!)
    }

    private lateinit var device: CompanionAppDevice


    private val runningApps: MutableStateFlow<List<CompanionApp>> = MutableStateFlow(emptyList())
    @Deprecated("Use more generic currentCompanionAppSession instead and cast if necessary")
    override val currentPKJSSession: StateFlow<PKJSApp?> = PKJSStateFlow(runningApps)

    override val currentCompanionAppSessions: StateFlow<List<CompanionApp>>
        get() = runningApps.asStateFlow()

    private suspend fun handleAppStop() {
        runningApps.value.forEach { it.stop() }
        runningApps.value = emptyList()
    }

    private suspend fun handleNewRunningApp(lockerEntry: LockerEntry, scope: CoroutineScope) {
        try {
            val pbw = PbwApp(lockerPBWCache.getPBWFileForApp(lockerEntry.id, lockerEntry.version, locker))
            if (runningApps.value.isNotEmpty()) {
                logger.w { "App ${lockerEntry.id} is already running, stopping it before starting a new one" }
                runningApps.value.forEach { it.stop() }
            }

            runningApps.value = createCompanionApps(pbw, lockerEntry).also { apps ->
                apps.forEach {
                    it.start(scope)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to init Companion app for app ${lockerEntry.id}: ${e.message}" }
            handleAppStop()
            return
        }
    }

    private fun createCompanionApps(
        pbw: PbwApp,
        lockerEntry: LockerEntry
    ): List<CompanionApp> {
        return buildList {
            val pkjsApp = if (pbw.hasPKJS) {
                val jsPath = lockerPBWCache.getPKJSFileForApp(lockerEntry.id, lockerEntry.version)
                PKJSApp(
                    device,
                    jsPath,
                    pbw.info,
                    lockerEntry,
                )
            } else null
            pkjsApp?.let { add(it) }
            if (libPebbleConfigFlow.value.watchConfig.appMessageToMultipleCompanions || pkjsApp == null) {
                createPlatformSpecificCompanionAppControl(
                    device = device,
                    appInfo = pbw.info,
                    pkjsRunning = pkjsApp != null,
                )?.let {
                    add(it)
                }
            }
        }
    }

    fun init(identifier: PebbleIdentifier, watchInfo: WatchInfo) {
        this.device = CompanionAppDevice(
            identifier,
            watchInfo,
            appMessagesService
        )
        appRunStateService.runningApp.onEach {
            handleAppStop()
            if (it != null) {
                val lockerEntry = lockerEntryDao.getEntry(it)
                lockerEntry?.let {
                    if (!it.systemApp) {
                        handleNewRunningApp(lockerEntry, scope)
                    }
                }
            }
        }.onCompletion {
            // Unsure if this is needed
            handleAppStop()
        }.launchIn(scope)
    }
}

/**
 * Hack to keep backwards compatibilty with the old ConnectedPebble.PKJS interface. It creates a state flow that only
 * exposes PKJSApp instances
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PKJSStateFlow(private val runningAppStateFlow: StateFlow<List<CompanionApp>>): StateFlow<PKJSApp?> {
    override val value: PKJSApp?
        get() = runningAppStateFlow.value.filterIsInstance<PKJSApp>().firstOrNull()
    override val replayCache: List<PKJSApp?>
        get() = runningAppStateFlow.replayCache.map { it.filterIsInstance<PKJSApp>().firstOrNull() }

    override suspend fun collect(collector: FlowCollector<PKJSApp?>): Nothing {
        runningAppStateFlow.map { it.filterIsInstance<PKJSApp>().firstOrNull() }.collect(collector)
        throw IllegalStateException("This collect should never stop because parent is a state flow")
    }
}

expect fun createPlatformSpecificCompanionAppControl(
    device: CompanionAppDevice,
    appInfo: PbwAppInfo,
    pkjsRunning: Boolean,
): CompanionApp?
