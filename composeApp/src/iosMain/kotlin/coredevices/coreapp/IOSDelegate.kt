package coredevices.coreapp

import co.touchlab.crashkios.crashlytics.enableCrashlytics
import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import cocoapods.FirebaseMessaging.FIRMessaging
import cocoapods.FirebaseMessaging.FIRMessagingAPNSTokenType
import cocoapods.GoogleSignIn.GIDSignIn
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.eygraber.uri.toUri
import com.mmk.kmpnotifier.extensions.onApplicationDidReceiveRemoteNotification
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import coredevices.analytics.AnalyticsBackend
import coredevices.coreapp.di.apiModule
import coredevices.coreapp.di.iosDefaultModule
import coredevices.coreapp.di.utilModule
import coredevices.coreapp.util.FileLogWriter
import coredevices.coreapp.util.initLogging
import coredevices.experimentalModule
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.watchModule
import coredevices.util.DoneInitialOnboarding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toNSDate
import okio.ByteString.Companion.toByteString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSUserActivity
import platform.Foundation.NSUserActivityTypeBrowsingWeb
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundFetchResult
import platform.UIKit.UIUserNotificationSettings
import platform.UIKit.UIUserNotificationTypeAlert
import platform.UIKit.UIUserNotificationTypeBadge
import platform.UIKit.UIUserNotificationTypeSound
import platform.UIKit.registerForRemoteNotifications
import platform.UIKit.registerUserNotificationSettings
import kotlin.time.Clock

object IOSDelegate : KoinComponent {
    private val logger = Logger.withTag("IOSDelegate")
    private val fileLogWriter: FileLogWriter by inject()
    private val commonAppDelegate: CommonAppDelegate by inject()
    private val pebbleAppDelegate: PebbleAppDelegate by inject()
    private val doneInitialOnboarding: DoneInitialOnboarding by inject()

    fun handleOpenUrl(url: NSURL): Boolean {
        logger.d("IOSDelegate handleOpenUrl $url")
        val pebbleDeepLinkHandler: PebbleDeepLinkHandler = get()
        return GIDSignIn.sharedInstance.handleURL(url) || pebbleDeepLinkHandler.handle(url.toUri())
    }

    private fun initPebble() {
        val pebbleDelegate: PebbleAppDelegate = get()
        pebbleDelegate.init()
    }

    fun didFinishLaunching(
        application: UIApplication,
        logAnalyticsEvent: (String, Map<String, Any>?) -> Unit,
        addGlobalAnalyticsProperty: (String, String?) -> Unit,
        setAnalyticsEnabled: (Boolean) -> Unit
    ): Boolean {
        logger.d("IOSDelegate didFinishLaunching")
        val analyticsBackendLogger = object : AnalyticsBackend {
            override fun logEvent(
                name: String,
                parameters: Map<String, Any>?
            ) {
                logAnalyticsEvent(name, parameters)
            }

            override fun addGlobalProperty(name: String, value: String?) {
                addGlobalAnalyticsProperty(name, value)
            }

            override fun setEnabled(enabled: Boolean) {
                setAnalyticsEnabled(enabled)
            }
        }
        val analyticsBackendModule = module {
            single { analyticsBackendLogger } bind AnalyticsBackend::class
        }
        startKoin {
            modules(
                iosDefaultModule,
                experimentalModule,
                apiModule,
                utilModule,
                watchModule,
                analyticsBackendModule,
            )
        }
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
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
        setupCrashlytics()
        initLogging()
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = REFRESH_TASK_IDENTIFIER,
            usingQueue = null,
        ) { task ->
            if (task == null) {
                logger.e { "task is null!" }
                return@registerForTaskWithIdentifier
            }
            runBlocking {
                commonAppDelegate.doBackgroundSync()
            }
            task.setTaskCompletedWithSuccess(true)
            requestBgRefresh()
        }
        requestBgRefresh()
        val appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "Unknown"
        val appVersionShort = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "Unknown"
        logger.i { "didFinishLaunching() appVersion=$appVersion appVersionShort=$appVersionShort" }
        // Can only use Koin after this point
        initPebble()
        GlobalScope.launch(Dispatchers.Main) {
            // Don't dop this before we request permissions (it requests permissions - we want to
            // manage that as part of onboarding).
            doneInitialOnboarding.doneInitialOnboarding.await()

            logger.d { "initializing push.." }
            application.registerUserNotificationSettings(
                UIUserNotificationSettings.settingsForTypes(
                    UIUserNotificationTypeAlert or UIUserNotificationTypeBadge or UIUserNotificationTypeSound,
                    null
                )
            )
            application.registerForRemoteNotifications()
            NotifierManager.initialize(
                configuration = NotificationPlatformConfiguration.Ios(
                    showPushNotification = false
                )
            )
        }
        commonAppDelegate.init()
        return true
    }

    private fun setupCrashlytics() {
        enableCrashlytics()
        setCrashlyticsUnhandledExceptionHook()
    }

    fun applicationWillTerminate() {
        fileLogWriter.logBlockingAndFlush(Severity.Info, "applicationWillTerminate", "IOSDelegate", null)
    }

    fun sceneDidBecomeActive() {
        logger.v { "sceneDidBecomeActive" }
        pebbleAppDelegate.onAppResumed()
    }

    fun sceneWillResignActive() {
        logger.v { "sceneWillResignActive" }
    }

    fun sceneWillEnterForeground() {
        logger.v { "sceneWillEnterForeground" }
    }

    fun sceneDidEnterBackground() {
        logger.v { "sceneDidEnterBackground" }
    }

    fun applicationDidEnterBackground() {
        fileLogWriter.logBlockingAndFlush(Severity.Info, "applicationDidEnterBackground", "IOSDelegate", null)
    }

    fun applicationDidRegisterForRemoteNotificationsWithDeviceToken(deviceToken: NSData) {
        val messaging = FIRMessaging.messaging()
        val initialSetup = messaging.APNSToken == null
        logger.d { "applicationDidRegisterForRemoteNotificationsWithDeviceToken: ${deviceToken.toByteString()}, initialSetup=$initialSetup" }
        val tokenType = if (isDevelopmentEntitlement()) {
            FIRMessagingAPNSTokenType.FIRMessagingAPNSTokenTypeSandbox
        } else {
            FIRMessagingAPNSTokenType.FIRMessagingAPNSTokenTypeProd
        }
        messaging.setAPNSToken(deviceToken, tokenType)
    }

    fun applicationDidReceiveRemoteNotification(userInfo: Map<Any?, *>, fetchCompletionHandler: (ULong) -> Unit) {
        val messaging = FIRMessaging.messaging()
        messaging.appDidReceiveMessage(userInfo)
        NotifierManager.onApplicationDidReceiveRemoteNotification(userInfo)
        fetchCompletionHandler(UIBackgroundFetchResult.UIBackgroundFetchResultNewData.value)
    }

    fun applicationWillContinue(userActivity: NSUserActivity): Boolean {
        if (userActivity.activityType != NSUserActivityTypeBrowsingWeb) {
            return false
        }
        val url = userActivity.webpageURL ?: return false
        return handleOpenUrl(url)
    }

    private fun isDevelopmentEntitlement(): Boolean {
        val path = NSBundle.mainBundle.pathForResource("embedded", "mobileprovision")
            ?: return false
        val data = NSData.dataWithContentsOfFile(path)
            ?.toByteString()
            ?.utf8()
            ?.replace("\t", "")
            ?: return false
        return data.contains("<key>aps-environment</key>\n<string>development</string>")
    }

    private fun requestBgRefresh() {
        val request = BGAppRefreshTaskRequest(REFRESH_TASK_IDENTIFIER)
        request.earliestBeginDate = (Clock.System.now() + BACKGROUND_REFRESH_PERIOD).toNSDate()
        val result = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        logger.d { "requestBgRefresh result = $result" }
    }
}

private const val REFRESH_TASK_IDENTIFIER = "coredevices.coreapp.sync"
