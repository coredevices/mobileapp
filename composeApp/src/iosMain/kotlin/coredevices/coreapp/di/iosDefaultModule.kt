package coredevices.coreapp.di

import CoreAppVersion
import PlatformContext
import PlatformShareLauncher
import coredevices.ExperimentalDevices
import coredevices.coreapp.RealGoogleAuthUtil
import coredevices.coreapp.util.AppUpdate
import coredevices.coreapp.util.IosAppUpdate
import coredevices.pebble.PebbleIosDelegate
import coredevices.util.CompanionDevice
import coredevices.util.CoreConfigFlow
import coredevices.util.GoogleAuthUtil
import coredevices.util.IOSPlatform
import coredevices.util.IosCompanionDevice
import coredevices.util.IosPermissionRequester
import coredevices.util.PermissionRequester
import coredevices.util.Platform
import coredevices.util.RequiredPermissions
import coredevices.util.models.ModelDownloadManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.DurationUnit

val iosDefaultModule = module {
    singleOf(::RealGoogleAuthUtil) bind GoogleAuthUtil::class
    singleOf(::PlatformShareLauncher)
    factory { params ->
        Darwin.create {
            configureRequest {
                setTimeoutInterval(params.get<Duration>().toDouble(DurationUnit.SECONDS))
            }
        }
    } bind HttpClientEngine::class
    singleOf(::AppContext)
    singleOf(::IOSPlatform) bind Platform::class
    singleOf(::PlatformContext)
    singleOf(::IosPermissionRequester) bind PermissionRequester::class
    singleOf(::IosCompanionDevice) bind CompanionDevice::class
    singleOf(::IosAppUpdate) bind AppUpdate::class
    single {
        val version = platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "unknown"
        val build = (platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toInt() ?: 0
        val buildStringAddition = if (build > 0) ".$build" else ""
        CoreAppVersion("$version$buildStringAddition")
    }
    single {
        val pebbleDelegate = get<PebbleIosDelegate>()
        val enabledFlow = get<CoreConfigFlow>().flow.map { it.enableIndex }
        val experimentalDevices = get<ExperimentalDevices>()
        RequiredPermissions(
            flow { emit(pebbleDelegate.requiredPermissions()) }.combine(enabledFlow) { permissions, enabled ->
                permissions + if (enabled) experimentalDevices.requiredRuntimePermissions() else emptySet()
            }
        )
    }
    singleOf(::ModelDownloadManager)
}