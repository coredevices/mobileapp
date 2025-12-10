package coredevices.pebble

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSURL

private val logger = Logger.withTag("PebbleDeepLinkHandler")

actual fun readNameFromContentUri(appContext: AppContext, uri: Uri): String? = uri.lastPathSegment

actual fun writeFile(
    appContext: AppContext,
    uri: Uri,
): Path? {
    val fileToWrite = getTempFilePath(appContext, "sideloaded_file")
    val nsUrl = NSURL(string = uri.toString())
    val securityScoped = nsUrl.startAccessingSecurityScopedResource()
    if (!securityScoped) {
        logger.i { "Failed to get security-scoped access for file URI: $uri. Will attempt direct access." }
    }
    try {
        val filePath = nsUrl.path
        if (filePath == null) {
            logger.w { "NSURL path is null for URI: $uri after attempting security scope access." }
            return null
        }
        val sourcePath = Path(filePath)
        if (!SystemFileSystem.exists(sourcePath)) {
            logger.w { "File does not exist at security-scoped path: $sourcePath for URI: $uri" }
            return null
        }
        val source = SystemFileSystem.source(sourcePath).buffered()
        source.use {
            SystemFileSystem.sink(fileToWrite).use { sink ->
                source.transferTo(sink)
            }
        }
        return fileToWrite
    } catch (e: Exception) {
        logger.e(e) { "Error writing file for $uri" }
        return null
    } finally {
        if (securityScoped) {
            nsUrl.stopAccessingSecurityScopedResource()
        }
    }
}