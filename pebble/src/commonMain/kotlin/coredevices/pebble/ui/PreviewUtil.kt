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
import com.eygraber.uri.Uri
import com.russhwolf.settings.Settings
import coredevices.coreapp.util.AppUpdate
import coredevices.coreapp.util.AppUpdatePlatformContent
import coredevices.coreapp.util.AppUpdateState
import coredevices.database.AppstoreCollection
import coredevices.database.AppstoreCollectionDao
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.Platform
import coredevices.pebble.RealPebbleDeepLinkHandler
import coredevices.pebble.account.BootConfig
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.account.FirestoreLockerEntry
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.account.UsersMeResponse
import coredevices.pebble.firmware.FirmwareUpdateUiTracker
import coredevices.pebble.services.AppStoreHome
import coredevices.pebble.services.AppStoreHomeResult
import coredevices.pebble.services.AppstoreCache
import coredevices.pebble.services.CoreUsersMe
import coredevices.pebble.services.PebbleWebServices
import coredevices.pebble.services.StoreAppResponse
import coredevices.pebble.services.StoreCategory
import coredevices.pebble.services.StoreSearchResult
import coredevices.pebble.weather.WeatherResponse
import coredevices.util.AppResumed
import coredevices.util.CompanionDevice
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.RequiredPermissions
import coredevices.util.WeatherUnit
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.FakeLibPebble
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.web.LockerAddResponse
import io.rebble.libpebblecommon.web.LockerModel
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import theme.AppTheme
import theme.CoreAppTheme
import theme.ThemeProvider
import kotlin.uuid.Uuid

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
        appStoreApplication = "",
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
        override suspend fun setUrl(url: String?) {}
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
    single { WatchHomeViewModel(get()) }
    single { NotificationAppScreenViewModel() }
    single { NotificationAppsScreenViewModel() }
    val storeSourceDao = object : AppstoreSourceDao {
        override suspend fun insertSource(source: AppstoreSource): Long = 0

        override fun getAllSources(): Flow<List<AppstoreSource>> {
            return MutableStateFlow(emptyList())
        }

        override fun getAllEnabledSourcesFlow(): Flow<List<AppstoreSource>> {
            return MutableStateFlow(emptyList())
        }

        override suspend fun getAllEnabledSources(): List<AppstoreSource> {
            return emptyList()
        }

        override suspend fun deleteSourceById(sourceId: Int) {}

        override suspend fun setSourceEnabled(sourceId: Int, isEnabled: Boolean) {}

        override suspend fun getSourceById(sourceId: Int): AppstoreSource? = null
    }
    val storeCollectionDao = object : AppstoreCollectionDao {
        override suspend fun insertOrUpdateCollection(collection: AppstoreCollection): Long = 1

        override suspend fun getCollection(
            sourceId: Int,
            slug: String,
            type: AppType
        ): AppstoreCollection? = null

        override suspend fun deleteCollection(collection: AppstoreCollection) {}

        override fun getAllCollectionsFlow(): Flow<List<AppstoreCollection>> {
            return MutableStateFlow(emptyList())
        }

        override suspend fun getAllCollections(): List<AppstoreCollection> = emptyList()
    }
    single { storeCollectionDao } bind AppstoreCollectionDao::class
    val webServices = object : PebbleWebServices {
        override suspend fun fetchUsersMePebble(): UsersMeResponse? = null
        override suspend fun fetchUsersMeCore(): CoreUsersMe? = null

        override suspend fun fetchPebbleLocker(): LockerModel? = null

        override suspend fun addToLegacyLocker(uuid: String): Boolean = true

        override suspend fun fetchAppStoreHome(
            type: AppType,
            hardwarePlatform: WatchType?,
            enabledOnly: Boolean,
            useCache: Boolean
        ): List<AppStoreHomeResult> = emptyList()

        override suspend fun searchAppStore(
            search: String,
            appType: AppType,
            watchType: WatchType
        ): List<Pair<AppstoreSource, StoreSearchResult>> = emptyList()

        override suspend fun addToLegacyLockerWithResponse(uuid: String): LockerAddResponse? = null

        override suspend fun addToLocker(
            entry: CommonAppType.Store,
            timelineToken: String?
        ): Boolean = true

        override suspend fun removeFromLegacyLocker(id: Uuid): Boolean = true

        override suspend fun getWeather(
            latitude: Double,
            longitude: Double,
            units: WeatherUnit,
            language: String
        ): WeatherResponse? = null

    }
    single { LockerViewModel(webServices, storeSourceDao) }
    single { storeSourceDao } bind AppstoreSourceDao::class
    single { configProvider } bind BootConfigProvider::class
    single { FakeLibPebble() } binds arrayOf(LibPebble::class, NotificationApps::class)
    factory { Platform.Android }
    single { appContext }
    single { account } bind PebbleAccount::class
    single { firmwareUpdateUiTracker } bind FirmwareUpdateUiTracker::class
    single { CoreAppVersion("1.0.0-preview") }
    val appstoreCache = object : AppstoreCache {
        override suspend fun readApp(
            id: String,
            parameters: Map<String, String>,
            source: AppstoreSource
        ): StoreAppResponse? = null

        override suspend fun writeApp(
            app: StoreAppResponse,
            parameters: Map<String, String>,
            source: AppstoreSource
        ) {}

        override suspend fun writeCategories(
            categories: List<StoreCategory>,
            type: AppType,
            source: AppstoreSource
        ) {}

        override suspend fun readCategories(
            type: AppType,
            source: AppstoreSource
        ): List<StoreCategory>? = null

        override suspend fun writeHome(
            home: AppStoreHome,
            type: AppType,
            source: AppstoreSource,
            parameters: Map<String, String>
        ) {}

        override suspend fun readHome(
            type: AppType,
            source: AppstoreSource,
            parameters: Map<String, String>
        ): AppStoreHome? = null

        override suspend fun clearCache() {}
    }
    single { appstoreCache } bind AppstoreCache::class
    single { NextBugReportContext() }
    val firestoreLocker = object : FirestoreLocker {
        override suspend fun readLocker(): List<FirestoreLockerEntry>? = null
        override suspend fun fetchLocker(forceRefresh: Boolean): LockerModelWrapper? = null
        override suspend fun addApp(entry: CommonAppType.Store, timelineToken: String?): Boolean = true
        override suspend fun removeApp(uuid: Uuid): Boolean = true
    }
    single { firestoreLocker } bind FirestoreLocker::class
    val coreConfig = CoreConfig(
        useNativeAppStoreV2 = true
    )
    single { CoreConfigFlow(MutableStateFlow(coreConfig)) }
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
    val initialLockerSync = object : PebbleDeepLinkHandler {
        override val initialLockerSync: StateFlow<Boolean> = MutableStateFlow(false)
        override val snackBarMessages: SharedFlow<String> = MutableSharedFlow()
        override val navigateToPebbleDeepLink: StateFlow<RealPebbleDeepLinkHandler.PebbleDeepLink?> = MutableStateFlow(null)
        override fun handle(uri: Uri?): Boolean = true
    }
    single { initialLockerSync } bind PebbleDeepLinkHandler::class
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
    searchAvailable = {},
    actions = {},
    title = {},
    overrideGoBack = MutableStateFlow(Unit),
    showSnackbar = { },
    scrollToTop = MutableStateFlow(Unit),
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

    override suspend fun setToken(token: String?, bootUrl: String?) {
        _loggedIn.value = token
    }

    override suspend fun setDevPortalId() {
        _devToken.value = "fake-dev-token"
    }
}
