package coredevices.coreapp.di

import AppUpdateTracker
import NextBugReportContext
import com.russhwolf.settings.Settings
import coredevices.CoreBackgroundSync
import coredevices.EnableExperimentalDevices
import coredevices.analytics.CoreAnalytics
import coredevices.analytics.RealCoreAnalytics
import coredevices.coreapp.CommonAppDelegate
import coredevices.coreapp.push.PushMessaging
import coredevices.coreapp.ui.navigation.CoreDeepLinkHandler
import coredevices.coreapp.ui.screens.BugReportProcessor
import coredevices.coreapp.ui.screens.OnboardingViewModel
import coredevices.coreapp.util.FileLogWriter
import coredevices.database.CoreDatabase
import coredevices.database.UserConfigDao
import coredevices.database.getCoreRoomDatabase
import coredevices.util.AppResumed
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.CoreConfigHolder
import coredevices.util.DoneInitialOnboarding
import coredevices.util.OAuthRedirectHandler
import coredevices.util.models.ModelDownloadManager
import coredevices.util.models.ModelManager
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreSettings
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.persistentCacheSettings
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import theme.RealThemeProvider
import theme.ThemeProvider

val utilModule = module {
    single<FirebaseFirestore> {
        Firebase.firestore.apply {
            settings = firestoreSettings {
                cacheSettings = persistentCacheSettings {
                    sizeBytes = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
                }
            }
        }
    }

    singleOf(::FileLogWriter)
    singleOf(::BugReportProcessor)
    singleOf(::NextBugReportContext)
    singleOf(::CommonAppDelegate) bind CoreBackgroundSync::class
    singleOf(::PushMessaging)
    singleOf(::CoreDeepLinkHandler)
    singleOf(::RealThemeProvider) bind ThemeProvider::class
    single { Settings() }
    viewModelOf(::OnboardingViewModel)
    singleOf(::EnableExperimentalDevices)
    singleOf(::AppResumed)
    singleOf(::DoneInitialOnboarding)
    singleOf(::AppUpdateTracker)
    singleOf(::RealCoreAnalytics) bind CoreAnalytics::class
    single { getCoreRoomDatabase(get()) }
    single { get<CoreDatabase>().analyticsDao() }
    single { get<CoreDatabase>().appstoreSourceDao() }
    single { get<CoreDatabase>().appstoreCollectionDao() }
    single { get<CoreDatabase>().weatherLocationDao() }
    single { get<CoreDatabase>().heartsDao() }
    singleOf(::UserConfigDao)
    single { CoreConfigHolder(defaultValue = CoreConfig(), get(), get()) }
    single { CoreConfigFlow(get<CoreConfigHolder>().config) }
    singleOf(::ModelManager)
    singleOf(::OAuthRedirectHandler)
}