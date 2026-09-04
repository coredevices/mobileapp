package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.AppContext

actual fun readBundledApp(appContext: AppContext, fileName: String): ByteArray? = null

actual fun readBundledPluginFile(
    appContext: AppContext,
    pluginDir: String,
    fileName: String,
): String? = Thread.currentThread().contextClassLoader
    ?.getResourceAsStream("plugins/$pluginDir/$fileName")
    ?.use { it.readBytes().decodeToString() }
