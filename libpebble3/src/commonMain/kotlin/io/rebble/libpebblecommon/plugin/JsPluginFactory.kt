package io.rebble.libpebblecommon.plugin

import io.ktor.client.HttpClient
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.js.HttpInterceptorManager
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.NetworkGate
import io.rebble.libpebblecommon.permissions.RunningApps
import io.rebble.libpebblecommon.plugin.files.PluginFileAccess
import io.rebble.libpebblecommon.plugin.files.PluginFileSystem
import kotlin.uuid.Uuid

class JsPluginFactory(
    private val appContext: AppContext,
    private val scope: LibPebbleCoroutineScope,
    private val httpClient: HttpClient,
    private val httpInterceptorManager: HttpInterceptorManager,
    private val permissions: AppPermissions,
    private val runningApps: RunningApps,
    private val fileAccess: PluginFileAccess,
    private val fileSystem: PluginFileSystem,
    private val logs: PluginLogs,
    private val oauthApi: PluginOAuthApi? = null,
) {
    fun create(
        pluginUuid: Uuid,
        name: String,
        manifest: PluginManifest,
        script: suspend () -> String,
    ): JsPlugin = JsPlugin(
        pluginUuid = pluginUuid,
        name = name,
        manifest = manifest,
        script = script,
        appContext = appContext,
        scope = scope,
        httpClient = httpClient,
        httpInterceptorManager = httpInterceptorManager,
        networkAccess = { prompt -> NetworkGate(permissions, name, pluginUuid, prompt) },
        runningApps = runningApps,
        fileAccess = fileAccess,
        fileSystem = fileSystem,
        logs = logs,
        oauthApi = oauthApi,
    )
}
