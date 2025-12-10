package coredevices.pebble

import android.provider.OpenableColumns
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import com.eygraber.uri.toAndroidUri
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.use

private val logger = Logger.withTag("PebbleDeepLinkHandler")

actual fun readNameFromContentUri(appContext: AppContext, uri: Uri): String? {
    try {
        val cursor = appContext.context.contentResolver.query(
            uri.toAndroidUri(),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }
    } catch (e: Exception) {
        logger.e(e) { "readNameFromContentUri" }
    }
    return null
}

actual fun writeFile(
    appContext: AppContext,
    uri: Uri
): Path? {
    val stream = appContext.context.contentResolver.openInputStream(uri.toAndroidUri())
    if (stream == null) {
        logger.e { "writeFile: stream is null" }
        return null
    }
    val fileToWrite = getTempFilePath(appContext, "sideloaded_file")
    stream.asSource().buffered().use { source ->
        SystemFileSystem.sink(fileToWrite).use { sink ->
            source.transferTo(sink)
        }
    }
    return fileToWrite
}