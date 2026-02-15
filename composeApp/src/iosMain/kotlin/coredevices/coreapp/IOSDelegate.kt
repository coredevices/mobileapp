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
import coredevices.coreapp.ui.navigation.CoreDeepLinkHandler
import coredevices.coreapp.util.FileLogWriter
import coredevices.coreapp.util.initLogging
import coredevices.experimentalModule
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.actions.PebbleAppActions
import coredevices.pebble.actions.PebbleNotificationActions
import coredevices.pebble.actions.PebbleQuietTimeActions
import coredevices.pebble.watchModule
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.timeline.TimelineColor
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.DoneInitialOnboarding
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
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
import platform.Foundation.NSDate
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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = Logger.withTag("IOSDelegate")

object IOSDelegate : KoinComponent {
    private val fileLogWriter: FileLogWriter by inject()
    private val commonAppDelegate: CommonAppDelegate by inject()
    private val pebbleAppDelegate: PebbleAppDelegate by inject()
    private val doneInitialOnboarding: DoneInitialOnboarding by inject()
    private val coreConfigHolder: CoreConfigHolder by inject()
    private val appActions: PebbleAppActions by inject()
    private val notificationActions: PebbleNotificationActions by inject()
    private val quietTimeActions: PebbleQuietTimeActions by inject()

    /**
     * Called from the iOS Shortcut to send a simple notification (title + body) to the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("sendSimpleNotificationToWatchWithTitleBody")
    fun sendSimpleNotificationToWatch(title: String, body: String) {
        notificationActions.sendSimpleNotification(title, body)
    }

    /**
     * Called from the iOS Shortcut to get the list of timeline colors for the picker.
     * Returns JSON: [{"id":"","title":"None"},{"id":"Orange","title":"Orange"},...]
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getTimelineColorsForShortcutsWithCompletion")
    fun getTimelineColorsForShortcuts(callback: (String) -> Unit) {
        GlobalScope.launch {
            fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            val none = """{"id":"","title":"None"}"""
            val colors = TimelineColor.entries.map { """{"id":"${it.name}","title":"${escape(it.displayName)}"}""" }
            val json = listOf(none) + colors
            withContext(Dispatchers.Main) { callback("[${json.joinToString(",")}]") }
        }
    }

    /**
     * Called from the iOS Shortcut to get the list of timeline icons for the picker.
     * Returns JSON: [{"id":"","title":"None"},{"id":"system://images/GENERIC_SMS","title":"Generic Sms"},...]
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getTimelineIconsForShortcutsWithCompletion")
    fun getTimelineIconsForShortcuts(callback: (String) -> Unit) {
        GlobalScope.launch {
            fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            fun iconTitle(icon: TimelineIcon) = icon.code.replace("system://images/", "").replace("_", " ")
                .lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            val none = """{"id":"","title":"None"}"""
            val icons = TimelineIcon.entries.map { """{"id":"${escape(it.code)}","title":"${escape(iconTitle(it))}"}""" }
            val json = listOf(none) + icons
            withContext(Dispatchers.Main) { callback("[${json.joinToString(",")}]") }
        }
    }

    /**
     * Called from the iOS Shortcut to send a notification with custom title, body, color and icon.
     * Pass null or empty string for colorName/iconCode to use none.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("sendDetailedNotificationToWatch")
    fun sendDetailedNotificationToWatch(title: String, body: String, colorName: String?, iconCode: String?) {
        notificationActions.sendDetailedNotification(
            title,
            body,
            colorName?.takeIf { it.isNotEmpty() },
            iconCode?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Called from the iOS Shortcut to enable or disable Quiet Time on the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setQuietTimeEnabledWithEnabled")
    fun setQuietTimeEnabled(enabled: Boolean) {
        quietTimeActions.setQuietTimeEnabled(enabled)
    }

    /**
     * Called from the iOS Shortcut to set Quiet Time show notifications: true = Show, false = Hide.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setQuietTimeShowNotificationsWithShow")
    fun setQuietTimeShowNotifications(show: Boolean) {
        quietTimeActions.setQuietTimeShowNotifications(show)
    }

    /**
     * Called from the iOS Shortcut to set Quiet Time interruptions: AllOff or PhoneCalls.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setQuietTimeInterruptionsWithAlertMaskName")
    fun setQuietTimeInterruptions(alertMaskName: String) {
        quietTimeActions.setQuietTimeInterruptions(alertMaskName)
    }

    /**
     * Called from the iOS Shortcut to enable or disable notification backlight on the watch.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setNotificationBacklightWithEnabled")
    fun setNotificationBacklight(enabled: Boolean) {
        notificationActions.setNotificationBacklight(enabled)
    }

    /**
     * Called from the iOS Shortcut to set notification filter: AllOn, PhoneCalls, or AllOff.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setNotificationFilterWithAlertMaskName")
    fun setNotificationFilter(alertMaskName: String) {
        notificationActions.setNotificationFilter(alertMaskName)
    }

    /**
     * Called from the iOS Shortcut to get the list of watchfaces for the picker.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getLockerWatchfacesForShortcutsWithCompletion")
    fun getLockerWatchfacesForShortcuts(callback: (String) -> Unit) {
        getLockerItemsForShortcutsByType(AppType.Watchface, callback)
    }

    /**
     * Called from the iOS Shortcut to get the list of watchapps for the picker.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getLockerWatchappsForShortcutsWithCompletion")
    fun getLockerWatchappsForShortcuts(callback: (String) -> Unit) {
        getLockerItemsForShortcutsByType(AppType.Watchapp, callback)
    }

    private fun getLockerItemsForShortcutsByType(type: AppType, callback: (String) -> Unit) {
        GlobalScope.launch {
            val libPebble: LibPebble = get()
            val list = libPebble.getAllLockerBasicInfo().first().filter { it.type == type }
            fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            val json = list.joinToString(",") { """{"id":"${it.id}","title":"${escape(it.title)}"}""" }.let { "[$it]" }
            withContext(Dispatchers.Main) {
                callback(json)
            }
        }
    }

    /**
     * Called from the iOS Shortcut to launch an app/watchface on the watch by UUID.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("launchAppByUuidWithUuid")
    fun launchAppByUuid(uuid: String) {
        appActions.launchApp(uuid)
    }

    /**
     * Called from the iOS Shortcut to get the list of notification apps for the picker.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("getNotificationAppsForShortcutsWithCompletion")
    fun getNotificationAppsForShortcuts(callback: (String) -> Unit) {
        GlobalScope.launch {
            val libPebble: LibPebble = get()
            val list = libPebble.notificationApps().first()
            fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            val json = list.joinToString(",") { entry ->
                val app = entry.app
                val muted = app.muteState == MuteState.Always
                """{"id":"${escape(app.packageName)}","title":"${escape(app.name)}","muted":$muted}"""
            }.let { "[$it]" }
            withContext(Dispatchers.Main) {
                callback(json)
            }
        }
    }

    /**
     * Called from the iOS Shortcut to mute or unmute a notification app by package name.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("setNotificationAppMuteStateWithPackageNameMute")
    fun setNotificationAppMuteState(packageName: String, mute: Boolean) {
        notificationActions.setAppMuteState(packageName, mute)
    }

    fun handleOpenUrl(url: NSURL): Boolean {
        logger.d("IOSDelegate handleOpenUrl $url")
        val pebbleDeepLinkHandler: PebbleDeepLinkHandler = get()
        val coreDeepLinkHandler: CoreDeepLinkHandler = get()
        val uri = url.toUri()
        return GIDSignIn.sharedInstance.handleURL(url) ||
                uri?.let {
                    pebbleDeepLinkHandler.handle(uri) || coreDeepLinkHandler.handle(uri)
                } ?: false
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
        val crashedPreviously = Firebase.crashlytics.didCrashOnPreviousExecution()
        if (crashedPreviously) {
            logger.e { "Previous app crash detected!" }
        }
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = REFRESH_TASK_IDENTIFIER,
            usingQueue = null,
        ) { task ->
            if (task == null) {
                logger.e { "task is null!" }
                return@registerForTaskWithIdentifier
            }
            runBlocking {
                commonAppDelegate.doBackgroundSync(force = false)
            }
            task.setTaskCompletedWithSuccess(true)
            requestBgRefresh(force = false, coreConfigHolder.config.value)
        }
        requestBgRefresh(force = false, coreConfigHolder.config.value)
        val appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "Unknown"
        val appVersionShort = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "Unknown"
        logger.i { "didFinishLaunching() appVersion=$appVersion appVersionShort=$appVersionShort" }
        // Can only use Koin after this point

        // Initialize NotifierManager early to prevent crashes when PushMessaging tries to use it
        NotifierManager.initialize(
            configuration = NotificationPlatformConfiguration.Ios(
                showPushNotification = false
            )
        )

        initPebble()
        GlobalScope.launch(Dispatchers.Main) {
            // Don't do this before we request permissions (it requests permissions - we want to
            // manage that as part of onboarding).
            doneInitialOnboarding.doneInitialOnboarding.await()

            logger.d { "registering for push notifications.." }
            application.registerUserNotificationSettings(
                UIUserNotificationSettings.settingsForTypes(
                    UIUserNotificationTypeAlert or UIUserNotificationTypeBadge or UIUserNotificationTypeSound,
                    null
                )
            )
            application.registerForRemoteNotifications()
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


}

private const val REFRESH_TASK_IDENTIFIER = "coredevices.coreapp.sync"

fun requestBgRefresh(force: Boolean, coreConfig: CoreConfig) {
    val interval = coreConfig.weatherSyncInterval
    BGTaskScheduler.sharedScheduler.getPendingTaskRequestsWithCompletionHandler { tasks ->
        val alreadyScheduledTask = (tasks as? List<BGAppRefreshTaskRequest>)?.find {
            it.identifier == REFRESH_TASK_IDENTIFIER
        }
        val alreadyScheduledNext = alreadyScheduledTask?.earliestBeginDate?.toKotlinInstant()
        val hasValidAlreadyScheduledTask = if (alreadyScheduledNext == null) {
            logger.d { "No existing scheduled task" }
            false
        } else {
            val timeToEarliestBegin = alreadyScheduledNext - Clock.System.now()
            if (timeToEarliestBegin > interval) {
                logger.d { "Existing scheduled task is too far in the future" }
                false
            } else {
                logger.d { "Existing valid task" }
                true
            }
        }

        if (hasValidAlreadyScheduledTask && !force) {
            return@getPendingTaskRequestsWithCompletionHandler
        }
        if (force) {
            logger.d { "Forcing reschedule because force=true" }
        }

        val request = BGAppRefreshTaskRequest(REFRESH_TASK_IDENTIFIER)
        request.earliestBeginDate = (Clock.System.now() + interval).toNSDate()
        try {
            val success = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
            logger.d { "requestBgRefresh: Scheduled new task (interval=$interval). Success = $success" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to submit task request" }
        }
    }
}