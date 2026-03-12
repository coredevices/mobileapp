package coredevices.coreapp

import co.touchlab.kermit.Logger
import com.cactus.CactusSTT
import com.cactus.services.CactusConfig
import com.russhwolf.settings.Settings
import coredevices.CoreBackgroundSync
import coredevices.ExperimentalDevices
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.CoreAnalytics
import coredevices.analytics.setUser
import coredevices.coreapp.api.BugReports
import coredevices.coreapp.push.PushMessaging
import coredevices.pebble.health.PlatformHealthSync
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import coredevices.coreapp.ui.screens.SHOWN_ONBOARDING
import coredevices.coreapp.util.AppUpdate
import coredevices.firestore.UsersDao
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.services.PebbleAccountProvider
import coredevices.pebble.weather.WeatherFetcher
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.DoneInitialOnboarding
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class CommonAppDelegate(
    private val pushMessaging: PushMessaging,
    private val bugReports: BugReports,
    private val settings: Settings,
    private val doneInitialOnboarding: DoneInitialOnboarding,
    private val analyticsBackend: AnalyticsBackend,
    private val coreAnalytics: CoreAnalytics,
    private val pebbleAppDelegate: PebbleAppDelegate,
    private val appUpdate: AppUpdate,
    private val weatherFetcher: WeatherFetcher,
    private val experimentalDevices: ExperimentalDevices,
    private val coreConfigHolder: CoreConfigHolder,
    private val appContext: AppContext,
    private val usersDao: UsersDao,
    private val pebbleAccountProvider: PebbleAccountProvider,
    private val firestoreLocker: FirestoreLocker,
    private val libPebble: LibPebble,
) : CoreBackgroundSync, KoinComponent {
    private val logger = Logger.withTag("CommonAppDelegate")
    private val syncInProgress = MutableStateFlow(false)
    private val platformHealthSync: PlatformHealthSync by inject()

    /**
     * Fixes case people updated to new version with the setting for model after using the previous default,
     * so we don't try to init with the new default they won't have downloaded.
     */
    private fun migrateCactusModelSetting() {
        GlobalScope.launch {
            try {
                if (!settings.hasKey("cactus_stt_model")) {
                    val model = CactusSTT().getVoiceModels()
                        .firstOrNull { it.isDownloaded }
                    model?.let {
                        settings.putString("cactus_stt_model", it.slug)
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "migrateCactusModelSetting failed" }
            }
        }
    }

    private fun oneTimeSetLockerOrderMode() {
        GlobalScope.launch {
            val key = "HAS_DONE_ONE_OFF_WATCHFACE_ORDER_SETTING"
            if (!settings.hasKey(key)) {
                val config = libPebble.config.value
                libPebble.updateConfig(
                    config.copy(
                        watchConfig = config.watchConfig.copy(
                            orderWatchfacesByLastUsed = true,
                        )
                    )
                )
                settings.putBoolean(key, true)
            }
        }
    }

    fun init() {
        usersDao.init()
        GlobalScope.launch(Dispatchers.Default) {
            usersDao.initUserDevToken(pebbleAccountProvider.get().devToken.value)
        }
        Firebase.auth.currentUser?.emailOrNull?.let {
            analyticsBackend.setUser(email = it)
        }
        CactusConfig.setTelemetryToken("fca9de5c-bbf0-42b4-bd8a-722252542f70")
        CommonBuildKonfig.CACTUS_PRO_KEY?.let { CactusConfig.setProKey(it) }
        migrateCactusModelSetting()
        pushMessaging.init()
        bugReports.init()
        GlobalScope.launch(Dispatchers.Default) {
            weatherFetcher.init()
            withContext(Dispatchers.Main) {
                experimentalDevices.init()
            }
        }
        firestoreLocker.init()
        oneTimeSetLockerOrderMode()
        // Health: request data from watch, sync to platform, and auto-sync on new data
        libPebble.requestHealthData()
        GlobalScope.launch(Dispatchers.Default) {
            platformHealthSync.sync()
        }
        platformHealthSync.startAutoSync(GlobalScope)
        if (settings.getBoolean(SHOWN_ONBOARDING, false)) {
            doneInitialOnboarding.onDoneInitialOnboarding()
        }
    }

    override suspend fun doBackgroundSync(scope: CoroutineScope, force: Boolean) {
        if (!syncInProgress.compareAndSet(false, true)) {
            logger.d { "Skipping background sync - already in progress" }
            return
        }

        val now = Clock.System.now()
        val lastFullSync = Instant.fromEpochMilliseconds(settings.getLong(KEY_LAST_FULL_SYNC_MS, 0L))
        val lastHealthSync = Instant.fromEpochMilliseconds(settings.getLong(KEY_LAST_HEALTH_SYNC_MS, 0L))
        val doFullSync = force || (now - lastFullSync) >= coreConfigHolder.config.value.regularSyncInterval
        val doHealthSync = force || (now - lastHealthSync) >= coreConfigHolder.config.value.healthSyncInterval
        logger.d { "doBackgroundSync: doFullSync=$doFullSync, doHealthSync=$doHealthSync" }
        if (doFullSync) {
            settings.putLong(KEY_LAST_FULL_SYNC_MS, now.toEpochMilliseconds())
        }
        val jobs = mutableListOf(
            scope.launch {
                weatherFetcher.fetchWeather(scope)
            },
        )
        if (doHealthSync) {
            settings.putLong(KEY_LAST_HEALTH_SYNC_MS, now.toEpochMilliseconds())
            jobs += scope.launch {
                platformHealthSync.sync()
                libPebble.requestHealthData()
            }
        }
        if (doFullSync) {
            jobs += scope.launch {
                coreAnalytics.processHeartbeat()
            }
            jobs += scope.launch {
                pebbleAppDelegate.performBackgroundWork(scope)
            }
            jobs += scope.launch {
                appUpdate.updateAvailable.value
            }
        }
        jobs.joinAll()
        syncInProgress.value = false
        logger.d { "doBackgroundSync / finished doFullSync=$doFullSync" }
    }

    override suspend fun timeSinceLastSync(): Duration {
        val now = Clock.System.now()
        val lastFullSync = Instant.fromEpochMilliseconds(settings.getLong(KEY_LAST_FULL_SYNC_MS, 0L))
        return now - lastFullSync
    }

    override fun updateFullSyncPeriod(interval: Duration) {
        coreConfigHolder.update(
            coreConfigHolder.config.value.copy(
                regularSyncInterval = interval,
            )
        )
    }

    override fun updateWeatherSyncPeriod(interval: Duration) {
        coreConfigHolder.update(
            coreConfigHolder.config.value.copy(
                weatherSyncInterval = interval,
            )
        )
        rescheduleBgRefreshTask(appContext, coreConfigHolder.config.value)
    }
}

expect fun rescheduleBgRefreshTask(appContext: AppContext, coreConfig: CoreConfig)

private const val KEY_LAST_FULL_SYNC_MS = "last_full_sync_time_ms"
private const val KEY_LAST_HEALTH_SYNC_MS = "last_health_sync_time_ms"