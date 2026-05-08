package coredevices.coreapp.di

import CoreAppVersion
import PlatformContext
import PlatformShareLauncher
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import coredevices.analytics.createAndroidAnalytics
import coredevices.coreapp.BuildConfig
import coredevices.coreapp.auth.RealAppleAuthUtil
import coredevices.coreapp.auth.RealGithubAuthUtil
import coredevices.coreapp.auth.RealGoogleAuthUtil
import coredevices.coreapp.sharing.ShareTargetActivity
import coredevices.coreapp.util.AndroidAppUpdate
import coredevices.coreapp.util.AppUpdate
import coredevices.pebble.PebbleAndroidDelegate
import coredevices.ring.RingDelegate
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
import io.rebble.libpebblecommon.shareintent.ShareTargetSync
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import android.content.ComponentName
import coredevices.util.R as UtilR
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
        val ringDelegate = get<RingDelegate>()
        RequiredPermissions(
            pebbleDelegate.requiredPermissions.combine(enabledFlow) { permissions, enabled ->
                permissions + if (enabled) ringDelegate.requiredRuntimePermissions() else emptySet()
            }
        )
    }
    single { createAndroidAnalytics(get()) }
    singleOf(::ModelDownloadManager)
    // PR 1: share-intent target. ShareTargetSync runs at app start (kicked
    // off in MainApplication.onCreate) and keeps Android Sharing Shortcuts
    // in sync with watchapps that declared `shareTarget` in their
    // package.json. We supply the activity ComponentName and a fallback icon
    // here because libpebble3 has no business knowing about composeApp's
    // activities or resources.
    single {
        val context: android.content.Context = get<io.rebble.libpebblecommon.connection.AppContext>().context
        ShareTargetSync(
            activityClass = ComponentName(context, ShareTargetActivity::class.java),
            // TODO: thread per-watchapp icons through from the locker so
            // each shortcut shows its real watchapp icon. For now everyone
            // gets the Pebble launcher icon.
            fallbackIconResId = UtilR.mipmap.ic_launcher,
        )
    }
}