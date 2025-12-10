package coredevices.pebble.ui

import CoreAppVersion
import NextBugReportContext
import PlatformUiContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.ColorImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.russhwolf.settings.Settings
import coredevices.coreapp.util.AppUpdate
import coredevices.coreapp.util.AppUpdatePlatformContent
import coredevices.coreapp.util.AppUpdateState
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.Platform
import coredevices.pebble.account.BootConfig
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.firmware.FirmwareUpdateUiTracker
import coredevices.util.AppResumed
import coredevices.util.CompanionDevice
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.RequiredPermissions
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.FakeLibPebble
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import theme.AppTheme
import theme.CoreAppTheme
import theme.ThemeProvider

expect @Composable
fun fakeAppContext(): AppContext

private val bootConfig = BootConfig.Config(
    locker = BootConfig.Config.Locker(
        addEndpoint = "",
        getEndpoint = "",
        removeEndpoint = "",
    ),
    webViews = BootConfig.Config.Webviews(
        appStoreWatchApps = "",
        appStoreWatchFaces = "",
    ),
    notifications = BootConfig.Config.Notifications(
        iosAppIcons = "",
    ),
    links = BootConfig.Config.Links(
        authenticationMe = "",
        usersMe = "",
    ),
    cohorts = BootConfig.Config.Cohorts(
        endpoint = "",
    ),
)

private fun fakePebbleModule(appContext: AppContext) = module {
    val account = FakePebbleAccount().apply {
        runBlocking {
            setToken("XXXXXXXX", "http://ddddfff")
        }
    }
    val configProvider = object : BootConfigProvider {
        override suspend fun setUrl(url: String) {}
        override fun getUrl(): String? = "http://fakeurl"
        override suspend fun getBootConfig(): BootConfig? = BootConfig(bootConfig)
    }
    val themeProvider = object : ThemeProvider {
        override val theme: StateFlow<CoreAppTheme> = MutableStateFlow(CoreAppTheme.Dark)
        override fun setTheme(theme: CoreAppTheme) {
        }
    }
    val firmwareUpdateUiTracker = object : FirmwareUpdateUiTracker {
        override fun didFirmwareUpdateCheckFromUi() {}
        override fun shouldUiUpdateCheck(): Boolean = false
        override fun maybeNotifyFirmwareUpdate(
            update: FirmwareUpdateCheckResult,
            identifier: PebbleIdentifier,
            watchName: String
        ) {
        }

        override fun firmwareUpdateIsInProgress(identifier: PebbleIdentifier) {}
    }
    single { themeProvider } bind ThemeProvider::class
    single { NotificationScreenViewModel() }
    single { WatchHomeViewModel() }
    single { NotificationAppScreenViewModel() }
    single { NotificationAppsScreenViewModel() }
    single { configProvider } bind BootConfigProvider::class
    single { FakeLibPebble() } binds arrayOf(LibPebble::class, NotificationApps::class)
    factory { Platform.Android }
    single { appContext }
    single { account } bind PebbleAccount::class
    single { firmwareUpdateUiTracker } bind FirmwareUpdateUiTracker::class
    single { CoreAppVersion("1.0.0-preview") }
    single { NextBugReportContext() }
    val requiredPermissions = RequiredPermissions(
        MutableStateFlow(
            setOf(
                Permission.Location,
                Permission.BackgroundLocation,
                Permission.PostNotifications,
                Permission.Bluetooth,
            )
        )
    )
    single { object : PermissionRequester(requiredPermissions, get<AppResumed>()) {
        override suspend fun requestPlatformPermission(
            permission: Permission,
            uiContext: PlatformUiContext
        ): PermissionResult {
            return PermissionResult.Granted
        }

        override suspend fun hasPermission(permission: Permission): Boolean {
            return false
        }

        override fun openPermissionsScreen(uiContext: PlatformUiContext) {
        }
    } } bind PermissionRequester::class
    single { PebbleFeatures(get()) }
    single { object : AppUpdate {
        override val updateAvailable: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.NoUpdateAvailable)

        override fun startUpdateFlow(
            uiContext: PlatformUiContext,
            update: AppUpdatePlatformContent
        ) {
        }
    } } bind AppUpdate::class
    single { object : CompanionDevice {
        override suspend fun registerDevice(
            identifier: PebbleIdentifier,
            uiContext: PlatformUiContext
        ) {

        }

        override fun hasApprovedDevice(identifier: PebbleIdentifier): Boolean {
            return true
        }

        override fun cdmPreviouslyCrashed(): Boolean {
            return false
        }
    } } bind CompanionDevice::class
    single { Settings() }
}

@Composable
fun fakePebbleModule() = fakePebbleModule(fakeAppContext())

val WrapperTopBarParams = TopBarParams(
    searchState = SearchState(query = "", typing = false),
    searchAvailable = {},
    actions = {},
    title = {},
    canGoBack = { false },
    goBack = MutableStateFlow(Unit),
    showSnackbar = { },
)

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    val previewHandler = AsyncImagePreviewHandler {
        ColorImage(Color.Red.toArgb())
    }
    val module = fakePebbleModule()
    KoinApplication(application = {
        modules(module)
    }) {
        CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler) {
            AppTheme {
                content()
            }
        }
    }
}

class FakePebbleAccount : PebbleAccount {
    private val _loggedIn = MutableStateFlow<String?>(null)
    private val _devToken = MutableStateFlow<String?>(null)

    override val loggedIn: StateFlow<String?>
        get() = _loggedIn
    override val devToken: StateFlow<String?>
        get() = _devToken

    override suspend fun setToken(token: String, bootUrl: String) {
        _loggedIn.value = token
    }

    override suspend fun setDevPortalId() {
        _devToken.value = "fake-dev-token"
    }
}
