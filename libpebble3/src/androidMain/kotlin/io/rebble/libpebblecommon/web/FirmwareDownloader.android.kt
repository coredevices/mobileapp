package io.rebble.libpebblecommon.web

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

internal actual fun getFirmwareDownloadDirectory(context: AppContext): Path {
    val dir = context.context.cacheDir.resolve("fw")
    dir.mkdirs()
    return Path(dir.absolutePath)
}