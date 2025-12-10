package coredevices.coreapp

import co.touchlab.kermit.Logger
import com.cactus.CactusSTT
import com.cactus.TranscriptionProvider
import com.cactus.services.CactusTelemetry
import com.russhwolf.settings.Settings
import coredevices.CoreBackgroundSync
import coredevices.EnableExperimentalDevices
import coredevices.ExperimentalDevices
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.CoreAnalytics
import coredevices.analytics.setUser
import coredevices.coreapp.api.BugReports
import coredevices.coreapp.push.PushMessaging
import coredevices.coreapp.ui.screens.SHOWN_ONBOARDING
import coredevices.coreapp.util.AppUpdate
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_FIREBASE_UPLOADS
import coredevices.pebble.weather.WeatherFetcher
import coredevices.util.DoneInitialOnboarding
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.crashlytics.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    private val enableExperimentalDevices: EnableExperimentalDevices,
    private val experimentalDevices: ExperimentalDevices,
) : CoreBackgroundSync {
    private val logger = Logger.withTag("CommonAppDelegate")
    private val syncInProgress = MutableStateFlow(false)

    /**
     * Fixes case people updated to new version with the setting for model after using the previous default,
     * so we don't try to init with the new default they won't have downloaded.
     */
    private fun migrateCactusModelSetting() {
        GlobalScope.launch {
            try {
                if (!settings.hasKey("cactus_stt_model")) {
                    val model = CactusSTT().getVoiceModels(TranscriptionProvider.WHISPER)
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

    fun init() {
        GlobalScope.launch(Dispatchers.Default) {
            if (Firebase.auth.currentUser == null) {
                logger.i { "Logging into firebase anonymously" }
                try {
                    Firebase.auth.signInAnonymously().let {
                        logger.d { "Firebase anonymous UID: ${it.user?.uid}" }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Failed to sign in anonymously" }
                }
            }
        }
        Firebase.auth.currentUser?.emailOrNull?.let {
            analyticsBackend.setUser(email = it)
        }
        CactusTelemetry.setTelemetryToken("fca9de5c-bbf0-42b4-bd8a-722252542f70")
        migrateCactusModelSetting()
        pushMessaging.init()
        bugReports.init()
        GlobalScope.launch(Dispatchers.Default) {
            // Suspend until we know if experimental devices are enabled,
            // could never happen if user doesn't have access, or could immediately happen if set locally
            enableExperimentalDevices.enabled.first { it }
            withContext(Dispatchers.Main) {
                experimentalDevices.init()
            }
        }
        if (settings.getBoolean(SHOWN_ONBOARDING, false)) {
            doneInitialOnboarding.onDoneInitialOnboarding()
        }
        if (!settings.getBoolean(KEY_ENABLE_FIREBASE_UPLOADS, true)) {
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
        }
    }

    override suspend fun doBackgroundSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            logger.d { "Skipping background sync - already in progress" }
            return
        }
        logger.d { "doBackgroundSync" }
        val jobs = listOf(
            GlobalScope.launch {
                coreAnalytics.processHeartbeat()
            },
            GlobalScope.launch {
                pebbleAppDelegate.performBackgroundWork()
            },
            GlobalScope.launch {
                appUpdate.updateAvailable.value
            },
            GlobalScope.launch {
                weatherFetcher.fetchWeather()
            },
        )
        jobs.joinAll()
        syncInProgress.value = false
        logger.d { "doBackgroundSync / finished" }
    }
}

val BACKGROUND_REFRESH_PERIOD = 4.hours