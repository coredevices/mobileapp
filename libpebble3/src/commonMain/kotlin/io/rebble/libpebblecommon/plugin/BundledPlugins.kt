package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.js.HttpInterceptorManager
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Reads a file shipped inside the app under `plugins/<pluginDir>/<fileName>`. Returns null when
 * it isn't present.
 */
expect fun readBundledPluginFile(appContext: AppContext, pluginDir: String, fileName: String): String?

/**
 * Reads a watchapp shipped inside the app under `bundled-apps/<fileName>`, or null when it isn't
 * there — the pbw is built by the `buildTestPbw` Gradle task, which is skipped on machines
 * without the Pebble SDK.
 */
expect fun readBundledApp(appContext: AppContext, fileName: String): ByteArray?

/**
 * Dev-time loader for JS plugins that ship inside the app rather than arriving in a pbw.
 * The manifest and script are exactly what a 3rd-party pbw would carry, so a plugin written
 * against this loads unchanged once pbw-delivered plugins land.
 */
class BundledPluginLoader(
    private val appContext: AppContext,
    private val registry: PluginRegistry,
    private val httpClient: HttpClient,
    private val httpInterceptorManager: HttpInterceptorManager,
    private val watchConfig: WatchConfigFlow,
    private val settings: Settings,
    private val scope: LibPebbleCoroutineScope,
) {
    private val logger = Logger.withTag("BundledPluginLoader")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var loaded = false

    /**
     * Nothing here has any meaning while the API is switched off: no plugins in the registry,
     * and no test watchapp sitting in a user's locker uninvited.
     */
    fun init(libPebble: LibPebble) {
        scope.launch {
            watchConfig.flow
                .map { it.watchConfig.enablePlugins }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        loadPlugins()
                        installBundledApps(libPebble)
                    } else {
                        removeBundledApps(libPebble)
                    }
                }
        }
    }

    /** Registered plugins stay registered when the setting goes off — the registry hides them. */
    private fun loadPlugins() {
        if (loaded) return
        loaded = true
        BUNDLED.forEach { dir -> load(dir) }
    }

    /**
     * Sideloads each bundled watchapp whose pbw differs from the copy already installed, so a
     * rebuilt phone app carries a rebuilt watchapp onto the watch. Never launches it: this runs
     * at startup, and taking over the watch's screen then would be rude.
     */
    private suspend fun installBundledApps(libPebble: LibPebble) {
        BUNDLED_APPS.forEach { app ->
            val pbw = readBundledApp(appContext, app.fileName)
            if (pbw == null) {
                logger.d { "no bundled app at ${app.fileName}" }
                return@forEach
            }
            val stamp = "${pbw.size}:${pbw.contentHashCode()}"
            val installed = libPebble.getLockerApp(app.uuid).firstOrNull() != null
            if (installed && settings.getStringOrNull(stampKey(app)) == stamp) {
                logger.d { "${app.fileName} already installed and unchanged" }
                return@forEach
            }
            val path = getTempFilePath(appContext, app.fileName)
            SystemFileSystem.sink(path).buffered().use { it.write(pbw) }
            // The pbw's version label doesn't move between builds, so removing it first is what
            // makes the watch treat this as a new app and fetch the new binary.
            if (installed) libPebble.removeApp(app.uuid)
            val ok = libPebble.sideloadApp(path, loadOnWatch = false)
            logger.i { "installed ${app.fileName}: $ok" }
            if (ok) settings.putString(stampKey(app), stamp)
        }
    }

    private suspend fun removeBundledApps(libPebble: LibPebble) {
        BUNDLED_APPS.forEach { app ->
            // Never installed — the common case for everyone who leaves the setting alone.
            if (settings.getStringOrNull(stampKey(app)) == null) return@forEach
            if (libPebble.getLockerApp(app.uuid).firstOrNull() == null) return@forEach
            logger.i { "removing ${app.fileName}" }
            libPebble.removeApp(app.uuid)
            settings.remove(stampKey(app))
        }
    }

    private fun stampKey(app: BundledApp) = "bundled_app_${app.uuid}"

    private fun load(dir: String) {
        val manifestJson = readBundledPluginFile(appContext, dir, "$dir-manifest.json")
        if (manifestJson == null) {
            logger.d { "no bundled plugin at $dir" }
            return
        }
        val manifest = try {
            json.decodeFromString(PluginManifest.serializer(), manifestJson)
        } catch (e: Exception) {
            logger.e(e) { "$dir: bad manifest" }
            return
        }
        val script = readBundledPluginFile(appContext, dir, manifest.script)
        if (script == null) {
            logger.e { "$dir: manifest names ${manifest.script} but it isn't bundled" }
            return
        }
        val configPageHtml = manifest.configPage
            ?.takeUnless { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { readBundledPluginFile(appContext, dir, it) }
        registry.registerPlugin(
            JsPlugin(
                manifest, script, appContext, scope, httpClient, httpInterceptorManager,
                configPageHtml,
            )
        )
        logger.i {
            "registered plugin ${manifest.name} " +
                "(${manifest.sources.size} sources, ${manifest.actions.size} actions)"
        }
    }

    private data class BundledApp(val fileName: String, val uuid: Uuid)

    private companion object {
        val BUNDLED = listOf("hue", "stocks")

        val BUNDLED_APPS = listOf(
            BundledApp("plugin-test.pbw", Uuid.parse("8b1c6b0e-7d6a-4cf2-a9b2-2c3f8b1c6b0e")),
            BundledApp("weather-face.pbw", Uuid.parse("3f6d2a90-5c41-4b7e-9d38-6a1e0c4f27b5")),
        )
    }
}
