package coredevices.pebble.ui

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

actual fun getTempFwPath(appContext: AppContext): Path {
    val cache = appContext.context.cacheDir
    val file = cache.resolve("temp.pbz")
    file.deleteOnExit()
    return Path(file.absolutePath)
}