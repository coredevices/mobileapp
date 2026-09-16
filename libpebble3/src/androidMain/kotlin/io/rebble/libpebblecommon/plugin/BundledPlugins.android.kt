package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.AppContext
import java.io.IOException

actual fun readBundledPluginFile(
    appContext: AppContext,
    pluginDir: String,
    fileName: String,
): String? = try {
    appContext.context.assets.open("plugins/$pluginDir/$fileName").use {
        it.readBytes().decodeToString()
    }
} catch (e: IOException) {
    null
}

actual fun readBundledApp(appContext: AppContext, fileName: String): ByteArray? = try {
    appContext.context.assets.open("bundled-apps/$fileName").use { it.readBytes() }
} catch (e: IOException) {
    null
}
