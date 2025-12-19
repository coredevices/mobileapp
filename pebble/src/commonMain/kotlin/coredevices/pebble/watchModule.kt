package coredevices.pebble

import co.touchlab.kermit.Logger
import com.algolia.client.api.SearchClient
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.account.FirestoreLockerDao
import coredevices.pebble.account.GithubAccount
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.account.PebbleTokenProvider
import coredevices.pebble.account.RealBootConfigProvider
import coredevices.pebble.account.RealGithubAccount
import coredevices.pebble.account.RealPebbleAccount
import coredevices.pebble.firmware.Cohorts
import coredevices.pebble.firmware.FirmwareUpdateCheck
import coredevices.pebble.firmware.FirmwareUpdateUiTracker
import coredevices.pebble.firmware.RealFirmwareUpdateUiTracker
import coredevices.pebble.services.AppstoreService
import coredevices.pebble.services.Github
import coredevices.pebble.services.CactusTranscription
import coredevices.pebble.services.LanguagePackRepository
import coredevices.pebble.services.Memfault
import coredevices.pebble.services.NullTranscriptionProvider
import coredevices.pebble.services.PebbleAccountProvider
import coredevices.pebble.services.PebbleBootConfigService
import coredevices.pebble.services.PebbleHttpClient
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.pebble.ui.AppStoreCollectionScreenViewModel
import coredevices.pebble.ui.AppstoreSettingsScreenViewModel
import coredevices.pebble.ui.ContactsViewModel
import coredevices.pebble.ui.LockerAppViewModel
import coredevices.pebble.ui.LockerViewModel
import coredevices.pebble.ui.NotificationAppScreenViewModel
import coredevices.pebble.ui.NotificationAppsScreenViewModel
import coredevices.pebble.ui.NotificationScreenViewModel
import coredevices.pebble.ui.WatchHomeViewModel
import coredevices.pebble.weather.WeatherFetcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.rebble.libpebblecommon.BleConfig
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.connection.LibPebble3
import io.rebble.libpebblecommon.connection.NotificationApps
import io.rebble.libpebblecommon.connection.TokenProvider
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.util.SystemGeolocation
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Clock

val watchModule = module {
    single {
        Logger.d("watchModule get LibPebble3")
        LibPebble3.create(
            get(),
            get(),
            get(),
            get(),
            get<GithubAccount>().loggedIn
                .map { it?.response?.accessToken }
                .stateIn(GlobalScope, started = SharingStarted.Lazily, initialValue = null),
            get()
        )
    } binds arrayOf(LibPebble3::class, NotificationApps::class, SystemGeolocation::class)

    includes(platformWatchModule)

    single { object : PebbleAccountProvider {
        override fun get(): PebbleAccount {
            return this@single.get()
        }
    } } bind PebbleAccountProvider::class
    singleOf(::PebbleAppDelegate)
    singleOf(::RealFirmwareUpdateUiTracker) bind FirmwareUpdateUiTracker::class
    factory<Clock> { Clock.System }
    singleOf(::RealPebbleAccount) bind PebbleAccount::class
    singleOf(::RealGithubAccount) bind GithubAccount::class
    singleOf(::FirestoreLockerDao)
    singleOf(::FirestoreLocker)
    factory { p ->
        AppstoreService(get(), get(), get(), p.get())
    }
    factoryOf(::RealBootConfigProvider) bind BootConfigProvider::class
    factoryOf(::RealPebbleWebServices) bind WebServices::class
    singleOf(::PebbleDeepLinkHandler)
    factoryOf(::PebbleHttpClient) bind PebbleBootConfigService::class
    factoryOf(::LibPebbleConfig)
    factoryOf(::Memfault)
    factoryOf(::Github)
    factoryOf(::Cohorts)
    factoryOf(::FirmwareUpdateCheck)
    factoryOf(::PebbleFeatures)
    factoryOf(::WeatherFetcher)
    factoryOf(::LanguagePackRepository)
    factoryOf(::PebbleTokenProvider) bind TokenProvider::class
    factoryOf(::NullTranscriptionProvider) bind TranscriptionProvider::class
    factory {
        WatchConfig(multipleConnectedWatchesSupported = false)
    }
    factory { NotificationConfig() }
    factory { BleConfig() }
    single {
        Json {
            // Important that everything uses this - otherwise future additions to web apis will
            // crash the app.
            ignoreUnknownKeys = true
        }
    }

    single {
        HttpClient {
            install(ContentNegotiation) {
                json(json = get())
            }
        }
    }
    singleOf(::CactusTranscription) bind TranscriptionProvider::class

    viewModelOf(::WatchHomeViewModel)
    viewModelOf(::NotificationScreenViewModel)
    viewModelOf(::NotificationAppScreenViewModel)
    viewModelOf(::NotificationAppsScreenViewModel)
    viewModelOf(::LockerViewModel)
    viewModelOf(::LockerAppViewModel)
    viewModelOf(::AppstoreSettingsScreenViewModel)
    viewModelOf(::ContactsViewModel)
    viewModel { p ->
        AppStoreCollectionScreenViewModel(
            get(),
            get(),
            get(),
            p.get(),
            p.get(),
            p.getOrNull()
        )
    }

    single { SearchClient(appId = "7683OW76EQ", apiKey = "252f4938082b8693a8a9fc0157d1d24f") }
}

expect val platformWatchModule: Module

enum class Platform {
    IOS,
    Android,
}