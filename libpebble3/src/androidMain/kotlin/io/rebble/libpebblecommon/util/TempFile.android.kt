package io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

actual fun getTempFilePath(
    appContext: AppContext,
    name: String,
    subdir: String?,
): Path {
    val cache = appContext.context.cacheDir
    val subdir = if (subdir == null) {
        cache
    } else {
        cache.resolve(subdir)
    }
    subdir.mkdirs()
    val file = subdir.resolve(name)
    file.deleteOnExit()
    return Path(file.absolutePath)
}