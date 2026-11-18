package coredevices.coreapp

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import androidx.annotation.RequiresApi
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.ExperimentalDevices
import coredevices.analytics.CoreAnalytics
import coredevices.coreapp.di.androidDefaultModule
import coredevices.coreapp.di.apiModule
import coredevices.coreapp.di.utilModule
import coredevices.coreapp.util.FileLogWriter
import coredevices.coreapp.util.initLogging
import coredevices.coreapp.util.registerBluetoothPairingDebugLogger
import coredevices.experimentalModule
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.watchModule
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.R
import coredevices.util.isDebugBuild
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.time.Instant
import kotlin.time.toJavaDuration

private val logger = Logger.withTag("MainApplication")
private const val KEY_LAST_REPORTED_EXIT_TIMESTAMP = "last_reported_exit_timestamp"

class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    private val pebbleAppDelegate: PebbleAppDelegate by inject()
    private val commonAppDelegate: CommonAppDelegate by inject()
    private val experimentalDevices: ExperimentalDevices by inject()
    private val fileLogWriter: FileLogWriter by inject()
    private val coreConfigHolder: CoreConfigHolder by inject()
    private val pebbleBackgroundManager: PebbleBackgroundManager by inject()
    private val coreAnalytics: CoreAnalytics by inject()
    private val settings: Settings by inject()

    companion object {
        // False if the process started without onCreate, e.g. backup restricted mode
        var koinStarted = false
            private set
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        if (AppContext(this).isDebugBuild()) {
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
        }
        startKoin {
            modules(
                module {
                    androidContext(this@MainApplication)
                },
                androidDefaultModule,
                experimentalModule,
                apiModule,
                utilModule,
                watchModule,
            )
        }
        koinStarted = true
        initLogging()
        logger.i { "onCreate() version = $appVersionName ($appVersionCode) ${if (AppContext(this).isDebugBuild()) "debug" else "release"}" }
        GlobalScope.launch(Dispatchers.IO) { dumpPreviousExitInfo() }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger.i { "Power state changed: isPowerSaveMode=${powerManager.isPowerSaveMode}" }
            }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        registerBluetoothPairingDebugLogger(this)
        configureFileOpenAliases()
        setupExceptionHandler()
        experimentalDevices.appInit()
        // Cactus telemetry is initialized via CommonAppDelegate.initCactus()
        pebbleAppDelegate.init()
        configureStrictMode()
        NotifierManager.initialize(
            configuration = NotificationPlatformConfiguration.Android(
                notificationIconResId = R.mipmap.ic_launcher,
                showPushNotification = false,
            )
        )
        scheduleBackgroundJob(AppContext(this), coreConfigHolder.config.value)
        commonAppDelegate.init()
        pebbleBackgroundManager.monitorToStartBackground()
    }

    private fun configureFileOpenAliases() {
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        setComponentEnabled("coredevices.coreapp.FileOpenActivity", !isSamsung)
        setComponentEnabled("coredevices.coreapp.SamsungFileOpenActivity", isSamsung)
    }

    private fun setComponentEnabled(className: String, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val component = ComponentName(this, className)
        if (packageManager.getComponentEnabledSetting(component) == state) return
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }

    private fun dumpPreviousExitInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val am =
                getSystemService(ActivityManager::class.java)
            // The newest record is often an isolated child (WebView), so log them all.
            val reasons = am.getHistoricalProcessExitReasons(packageName, 0, 0)
            // First run: only report the most recent exit, not the whole history.
            val lastReported = settings.getLongOrNull(KEY_LAST_REPORTED_EXIT_TIMESTAMP)
                ?: reasons.filter { it.processName == packageName }.maxOfOrNull { it.timestamp - 1 }
                ?: 0L
            reasons.forEach { info ->
                val time = Instant.fromEpochMilliseconds(info.timestamp)
                logger.i {
                    "Previous exit @ $time process=${info.processName} " +
                            "reason=${reasonName(info.reason)} " +
                            "description=${info.description} importance=${info.importance} " +
                            "pss=${info.pss} rss=${info.rss} status=${info.status}"
                }
                if (info.timestamp > lastReported && info.processName == packageName) {
                    coreAnalytics.logEvent(
                        "app.previous_exit",
                        mapOf(
                            "reason" to reasonName(info.reason),
                            "description" to (info.description ?: ""),
                            "importance" to info.importance,
                            "pss_kb" to info.pss,
                            "rss_kb" to info.rss,
                            "status" to info.status,
                        )
                    )
                }
            }
            reasons.maxOfOrNull { it.timestamp }?.let { settings[KEY_LAST_REPORTED_EXIT_TIMESTAMP] = it }
        }
    }

    @RequiresApi(30)
    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH_JAVA"
        ApplicationExitInfo.REASON_CRASH_NATIVE ->
            "CRASH_NATIVE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "OOM"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED ->
            "USER_REQUESTED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
            -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED ->
            "DEPENDENCY_DIED"
        else -> "OTHER($reason)"
    }

    private fun configureStrictMode() {
        if (AppContext(this).isDebugBuild()) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    // .penaltyDeath() // Crash the app on violation (useful for actively debugging)
                    // .penaltyDialog() // Show a dialog (can be intrusive)
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    // .penaltyDeath()
                    .build()
            )
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        fileLogWriter.logBlockingAndFlush(Severity.Info, "onLowMemory", "MainApplication", null)
    }

    override fun onTerminate() {
        super.onTerminate()
        fileLogWriter.logBlockingAndFlush(Severity.Info, "onTerminate", "MainApplication", null)
    }

    private fun setupExceptionHandler() {
        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                fileLogWriter.logBlockingAndFlush(
                    Severity.Error,
                    "Unhandled exception in thread ${thread.name}: ${throwable.message}",
                    "MainApplication",
                    throwable
                )
            } finally {
                // Must always run: it's what kills the process
                existingHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            // Cached hardware bitmaps keep their GPU buffers after backgrounding (the foreground service blocks trimming).
            .memoryCache(null)
            .components {
                add(SvgDecoder.Factory())
                // Below P, GIFs show their first frame.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                }
            }
            .build()
    }
}

fun scheduleBackgroundJob(appContext: AppContext, coreConfig: CoreConfig) {
    logger.d { "scheduleBackgroundJob for ${coreConfig.weatherSyncInterval}" }
    val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
        repeatInterval = coreConfig.weatherSyncInterval.toJavaDuration(),
    ).setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    ).build()
    // WorkManager.getInstance opens its database on first use
    GlobalScope.launch(Dispatchers.IO) {
        try {
            WorkManager.getInstance(appContext.context).enqueueUniquePeriodicWork(
                uniqueWorkName = "core_refresh",
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                request = workRequest,
            )
        } catch (e: Throwable) {
            // Broken OEM builds throw NoSuchMethodError from WorkManager init; sync just won't run.
            logger.e(e) { "Failed to schedule background job" }
        }
    }
}