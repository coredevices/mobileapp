package io.rebble.libpebblecommon.locker

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

actual fun getLockerPBWCacheDirectory(context: AppContext): Path {
    val dir = context.context.filesDir.resolve("pbw")
    dir.mkdirs()
    return Path(dir.absolutePath)
}

actual fun getLockerPBWCacheLegacyDirectory(context: AppContext): Path? {
    val legacy = context.context.cacheDir.resolve("pbw")
    return if (legacy.exists()) Path(legacy.absolutePath) else null
}