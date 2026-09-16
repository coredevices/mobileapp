package coredevices.pebble.ui

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

actual fun getTempFwPath(appContext: AppContext): Path {
    val cache = appContext.context.cacheDir
    val file = cache.resolve("temp.pbz")
    file.deleteOnExit()
    return Path(file.absolutePath)
}

// filesDir/sideload.pbz — a fixed drop path a .pbz can be pushed to (adb) and sideloaded without a
// picker. Android also has FirmwareSideloadReceiver; this mirrors the iOS QA path.
actual fun getSideloadDropPath(appContext: AppContext): Path {
    val file = appContext.context.filesDir.resolve("sideload.pbz")
    return Path(file.absolutePath)
}