package coredevices.coreapp.di

import CoreAppVersion
import PlatformContext
import PlatformShareLauncher
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import coredevices.analytics.createAndroidAnalytics
import coredevices.coreapp.BuildConfig
import coredevices.ExperimentalDevices
import coredevices.coreapp.auth.RealAppleAuthUtil
import coredevices.coreapp.auth.RealGithubAuthUtil
import coredevices.coreapp.auth.RealGoogleAuthUtil
import coredevices.coreapp.util.AndroidAppUpdate
import coredevices.coreapp.util.AppUpdate
import coredevices.pebble.PebbleAndroidDelegate
import coredevices.util.AndroidCompanionDevice
import coredevices.util.AndroidPermissionRequester
import coredevices.util.AndroidPlatform
import coredevices.util.auth.AppleAuthUtil
import coredevices.util.CompanionDevice
import coredevices.util.CoreConfigFlow
import coredevices.util.auth.GoogleAuthUtil
import coredevices.util.PermissionRequester
import coredevices.util.Platform
import coredevices.util.RequiredPermissions
import coredevices.util.auth.GitHubAuthUtil
import coredevices.util.models.ModelDownloadManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.toJavaDuration

val androidDefaultModule = module {
    singleOf(::RealGoogleAuthUtil) bind GoogleAuthUtil::class
    singleOf(::RealAppleAuthUtil) bind AppleAuthUtil::class
    singleOf(::RealGithubAuthUtil) bind GitHubAuthUtil::class
    factory { params ->
        OkHttp.create {
            config {
                readTimeout(params.get<Duration>().toJavaDuration())
            }
        }
    } bind HttpClientEngine::class
    singleOf(::PlatformShareLauncher)
    singleOf(::AndroidPlatform) bind Platform::class
    single { CoreAppVersion(BuildConfig.VERSION_NAME) }
    factory { AppUpdateManagerFactory.create(get()) }
    singleOf(::PlatformContext)
    singleOf(::AndroidPermissionRequester) bind PermissionRequester::class
    singleOf(::AndroidCompanionDevice) bind CompanionDevice::class
    singleOf(::AndroidAppUpdate) bind AppUpdate::class
    single {
        val pebbleDelegate = get<PebbleAndroidDelegate>()
        val enabledFlow = get<CoreConfigFlow>().flow.map { it.enableIndex }
        val experimentalDevices = get<ExperimentalDevices>()
        RequiredPermissions(
            pebbleDelegate.requiredPermissions.combine(enabledFlow) { permissions, enabled ->
                permissions + if (enabled) experimentalDevices.requiredRuntimePermissions() else emptySet()
            }
        )
    }
    single { createAndroidAnalytics(get()) }
    singleOf(::ModelDownloadManager)
}