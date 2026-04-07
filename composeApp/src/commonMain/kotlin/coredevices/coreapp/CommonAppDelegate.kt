package coredevices.coreapp

import co.touchlab.kermit.Logger
import com.mmk.kmpnotifier.notification.Notifier
import com.mmk.kmpnotifier.notification.NotifierManager
import coredevices.util.transcription.CactusModelPathProvider
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
import coredevices.util.models.CactusSTTMode
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
    private val platformHealthSync: PlatformHealthSync,
) : CoreBackgroundSync {
    private val logger = Logger.withTag("CommonAppDelegate")
    private val syncInProgress = MutableStateFlow(false)

    private fun initCactus() {
        val modelProvider = try {
            org.koin.mp.KoinPlatform.getKoin().get<CactusModelPathProvider>()
        } catch (e: Exception) {
            logger.w(e) { "Cactus model provider not available" }
            return
        }
        try {
            modelProvider.initTelemetry()
        } catch (e: Exception) {
            logger.w(e) { "Cactus telemetry init skipped" }
        }
        try {
            val incompatible = modelProvider.getIncompatibleModels()
            if (incompatible.isNotEmpty()) {
                logger.d { "Incompatible models found, deleting and notifying user to migrate" }
                coreConfigHolder.update(
                    coreConfigHolder.config.value.copy(
                        sttConfig = coreConfigHolder.config.value.sttConfig.copy(
                            mode = CactusSTTMode.RemoteOnly,
                            modelName = null,
                        )
                    )
                )
                incompatible.forEach {
                    try {
                        modelProvider.deleteModel(it)
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to delete incompatible model $it" }
                    }
                }
                NotifierManager.getLocalNotifier().notify(
                    "Offline voice recognition",
                    "We've made improvements to our offline voice recognition. Please open the app to download the new model from settings."
                )
            }
        } catch (e: Exception) {
            logger.w(e) { "Cactus incompatible model check skipped" }
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
        initCactus()
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
        val lastFullSync =
            Instant.fromEpochMilliseconds(settings.getLong(KEY_LAST_FULL_SYNC_MS, 0L))
        val lastHealthSync = Instant.fromEpochMilliseconds(settings.getLong(KEY_LAST_HEALTH_SYNC_MS, 0L))
        val doFullSync =
            force || (now - lastFullSync) >= coreConfigHolder.config.value.regularSyncInterval
        val doHealthSync = force || (now - lastHealthSync) >= coreConfigHolder.config.value.healthSyncInterval
        logger.d { "doBackgroundSync: doFullSync=$doFullSync" }
        try {
            if (doFullSync) {
                settings.putLong(KEY_LAST_FULL_SYNC_MS, now.toEpochMilliseconds())
            }
            val jobs = buildList {
                add(
                    scope.launch {
                        weatherFetcher.fetchWeather(scope)
                    }
                )
                add(
                    scope.launch {
                        platformHealthSync.sync()
                        libPebble.requestHealthData()
                    }
                )
                if (doFullSync) {
                    add(scope.launch {
                        coreAnalytics.processHeartbeat()
                    })
                    add(scope.launch {
                        pebbleAppDelegate.performBackgroundWork(scope)
                    })
                    add(scope.launch {
                        appUpdate.updateAvailable.value
                    })
                }
            }
            jobs.joinAll()
        } finally {
            syncInProgress.value = false
        }
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