package coredevices.coreapp

import android.app.Application
import android.os.StrictMode
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
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.cactus.CactusContextInitializer
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
import coredevices.util.R
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.time.toJavaDuration

class MainApplication : Application(), SingletonImageLoader.Factory {
    private val pebbleAppDelegate: PebbleAppDelegate by inject()
    private val commonAppDelegate: CommonAppDelegate by inject()
    private val fileLogWriter: FileLogWriter by inject()
    private val logger = Logger.withTag("MainApplication")

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
        setupExceptionHandler()
        CactusContextInitializer.initialize(this.applicationContext)
        pebbleAppDelegate.init()
        configureStrictMode()
        NotifierManager.initialize(
            configuration = NotificationPlatformConfiguration.Android(
                notificationIconResId = R.mipmap.ic_launcher,
                showPushNotification = false,
            )
        )
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = BACKGROUND_REFRESH_PERIOD.toJavaDuration(),
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            uniqueWorkName = "core_refresh",
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = workRequest,
        )
        commonAppDelegate.init()
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
            }
            .build()
    }
}