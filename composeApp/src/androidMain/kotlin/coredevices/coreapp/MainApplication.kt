package coredevices.coreapp

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import androidx.annotation.RequiresApi
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
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import coredevices.coreapp.di.androidDefaultModule
import coredevices.coreapp.di.apiModule
import coredevices.coreapp.di.utilModule
import coredevices.coreapp.util.FileLogWriter
import coredevices.coreapp.util.initLogging
import coredevices.experimentalModule
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.watchModule
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.R
import io.rebble.libpebblecommon.connection.AppContext
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.time.Instant
import kotlin.time.toJavaDuration

private val logger = Logger.withTag("MainApplication")

class MainApplication : Application(), SingletonImageLoader.Factory {
    private val pebbleAppDelegate: PebbleAppDelegate by inject()
    private val commonAppDelegate: CommonAppDelegate by inject()
    private val fileLogWriter: FileLogWriter by inject()
    private val coreConfigHolder: CoreConfigHolder by inject()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
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
        initLogging()
        logger.i { "onCreate() version = ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}" }
        dumpPreviousExitInfo()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger.i { "Power state changed: isPowerSaveMode=${powerManager.isPowerSaveMode}" }
            }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        setupExceptionHandler()
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
    }

    private fun dumpPreviousExitInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val am =
                getSystemService(ActivityManager::class.java)
            val reasons =
                am.getHistoricalProcessExitReasons(packageName, 0, 5)
            reasons.firstOrNull()?.let { info ->
                val time = Instant.fromEpochMilliseconds(info.timestamp)
                logger.i {
                    "Previous exit @ $time reason=${reasonName(info.reason)} " +
                            "description=${info.description} importance=${info.importance} " +
                            "pss=${info.pss} rss=${info.rss} status=${info.status}"
                }
            }
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
        if (BuildConfig.DEBUG) {
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
            fileLogWriter.logBlockingAndFlush(
                Severity.Error,
                "Unhandled exception in thread ${thread.name}: ${throwable.message}",
                "MainApplication",
                throwable
            )
            // Allow Firebase to also handle the exception
            existingHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
                // Trying to see if not using AnimatedImageDecoder fixes memory leaks
//                if (SDK_INT >= 28) {
//                    add(AnimatedImageDecoder.Factory())
//                } else {
                    add(GifDecoder.Factory())
//                }
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
    WorkManager.getInstance(appContext.context).enqueueUniquePeriodicWork(
        uniqueWorkName = "core_refresh",
        existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
        request = workRequest,
    )
}