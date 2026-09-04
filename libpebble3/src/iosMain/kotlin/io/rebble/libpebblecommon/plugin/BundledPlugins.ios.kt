package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import platform.Foundation.NSBundle

/**
 * The Xcode project references `androidMain/assets/plugins` as a folder, so the directory tree is
 * copied into the bundle as-is and adding a plugin needs no project change.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun readBundledApp(appContext: AppContext, fileName: String): ByteArray? {
    val resources = NSBundle.mainBundle.resourcePath ?: return null
    val data = NSData.dataWithContentsOfFile("$resources/bundled-apps/$fileName") ?: return null
    if (data.length.toInt() == 0) return null
    return ByteArray(data.length.toInt()).apply {
        usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    }
}

actual fun readBundledPluginFile(
    appContext: AppContext,
    pluginDir: String,
    fileName: String,
): String? {
    val resources = NSBundle.mainBundle.resourcePath ?: return null
    val path = "$resources/plugins/$pluginDir/$fileName"
    return try {
        SystemFileSystem.source(Path(path)).buffered().use { it.readString() }
    } catch (e: Exception) {
        null
    }
}
