package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.locker.Locker
import io.rebble.libpebblecommon.locker.LockerWrapper
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlin.uuid.Uuid

/**
 * Reads a pbw shipped inside the app under `bundled-apps/<fileName>`, or null when it isn't there —
 * the pbw is built by the `buildTestAppPbws` Gradle task, skipped on machines without the Pebble SDK.
 */
expect fun readBundledApp(appContext: AppContext, fileName: String): ByteArray?

/**
 * Sideloads the demo pbws (watchapps and plugin-only) into the locker while plugins are enabled, and
 * removes them when disabled. To be removed once plugin development is complete and these are on
 * the app store.
 */
class BundledAppInstaller(
    private val appContext: AppContext,
    private val watchConfig: WatchConfigFlow,
    private val settings: Settings,
    private val scope: LibPebbleCoroutineScope,
    private val locker: Locker,
) {
    private val logger = Logger.withTag("BundledAppInstaller")

    fun init(libPebble: LibPebble) {
        scope.launch {
            watchConfig.flow
                .map { it.watchConfig.enablePlugins }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) installBundledApps(libPebble) else removeBundledApps(libPebble)
                }
        }
    }

    /**
     * Sideloads each bundled pbw whose bytes differ from the copy already installed, so a rebuilt
     * phone app carries a rebuilt pbw onto the watch. Never launches it: taking over the watch's
     * screen at startup would be rude.
     */
    private suspend fun installBundledApps(libPebble: LibPebble) {
        BUNDLED_APPS.forEach { app ->
            val pbw = readBundledApp(appContext, app.fileName)
            if (pbw == null) {
                logger.d { "no bundled app at ${app.fileName}" }
                return@forEach
            }
            val stamp = "${pbw.size}:${pbw.contentHashCode()}"
            val existing = libPebble.getLockerApp(app.uuid).firstOrNull()
            if (existing.isFromStore()) {
                logger.d { "${app.fileName} is installed from a store; leaving it" }
                return@forEach
            }
            val installed = existing != null
            val stamped = settings.getStringOrNull(stampKey(app))
            if (!installed && stamped != null) {
                logger.d { "${app.fileName} was removed by the user; leaving it out" }
                return@forEach
            }
            if (installed && stamped == stamp) {
                logger.d { "${app.fileName} already installed and unchanged" }
                return@forEach
            }
            val path = getTempFilePath(appContext, app.fileName)
            SystemFileSystem.sink(path).buffered().use { it.write(pbw) }
            // The pbw's version label doesn't move between builds, so removing it first is what
            // makes the watch treat this as a new app and fetch the new binary.
            if (installed) locker.removeApp(app.uuid, keepSecrets = true)
            val ok = libPebble.sideloadApp(path, loadOnWatch = false)
            logger.i { "installed ${app.fileName}: $ok" }
            if (!ok) return@forEach
            settings.putString(stampKey(app), stamp)
        }
    }

    private suspend fun removeBundledApps(libPebble: LibPebble) {
        BUNDLED_APPS.forEach { app ->
            // Never installed — the common case for everyone who leaves the setting alone.
            if (settings.getStringOrNull(stampKey(app)) == null) return@forEach
            val existing = libPebble.getLockerApp(app.uuid).firstOrNull()
            if (existing == null) {
                // Removed by the user: forget that, so turning plugins back on reinstalls it.
                settings.remove(stampKey(app))
                return@forEach
            }
            if (existing.isFromStore()) return@forEach
            logger.i { "removing ${app.fileName}" }
            libPebble.removeApp(app.uuid)
            settings.remove(stampKey(app))
        }
    }

    private fun stampKey(app: BundledApp) = "bundled_app_${app.uuid}"

    private fun LockerWrapper?.isFromStore() = this is LockerWrapper.NormalApp && !sideloaded

    private data class BundledApp(val fileName: String, val uuid: Uuid)

    private companion object {
        val BUNDLED_APPS = listOf(
            BundledApp("plugin-test.pbw", Uuid.parse("8b1c6b0e-7d6a-4cf2-a9b2-2c3f8b1c6b0e")),
            BundledApp("find-my-phone.pbw", Uuid.parse("438e789f-1f16-424e-829c-0c2f4865fbe8")),
            BundledApp("weather-face.pbw", Uuid.parse("3f6d2a90-5c41-4b7e-9d38-6a1e0c4f27b5")),
            BundledApp("met-weather.pbw", Uuid.parse("323dc117-55bd-455f-9ee4-4d0faba56ea1")),
            BundledApp("hue.pbw", Uuid.parse("6f9c1a44-3d1e-4b8a-9c2f-0d5e7a1b3c40")),
            BundledApp("stocks.pbw", Uuid.parse("2c4b7f10-9a3d-4e6b-8f21-5d0c7e9a1b34")),
            BundledApp("notion.pbw", Uuid.parse("c7ad904e-40d5-4cb5-8f13-1d1d3c6a7e21")),
            BundledApp("ticktick.pbw", Uuid.parse("b1740936-c2e8-4dd6-b4cf-a85058109ac0")),
            BundledApp("todoist.pbw", Uuid.parse("0d520cba-326c-4a22-a269-d1ab1ae38977")),
//            BundledApp("spotify.pbw", Uuid.parse("f7c51b03-6b8f-4ce8-9ce4-7a406dcf8ca7")), needs a prod dev account users can actually log in to
        )
    }
}
