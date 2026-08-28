package coredevices.pebble

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path
import java.io.File

private val usesMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

private fun legacyDirectory(appContext: AppContext): File {
    val base = appContext.context.getExternalFilesDir(null) ?: appContext.context.filesDir
    return File(base, WATCH_LOGS_FOLDER_NAME)
}

actual fun saveWatchLogCopy(appContext: AppContext, source: Path, fileName: String): String? {
    val sourceFile = File(source.toString())
    if (!usesMediaStore) {
        val directory = legacyDirectory(appContext).apply { mkdirs() }
        val target = File(directory, fileName)
        sourceFile.inputStream().use { input -> target.outputStream().use(input::copyTo) }
        return target.absolutePath
    }
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$WATCH_LOGS_FOLDER_NAME"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
    }
    val resolver = appContext.context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
    val written = resolver.openOutputStream(uri)?.use { output ->
        sourceFile.inputStream().use { input -> input.copyTo(output) }
    }
    if (written == null) {
        resolver.delete(uri, null, null)
        return null
    }
    return "$relativePath/$fileName"
}

actual fun pruneWatchLogCopies(appContext: AppContext, keep: Int) {
    if (!usesMediaStore) {
        legacyDirectory(appContext).listFiles()
            .orEmpty()
            .filter { it.name.startsWith(WATCH_LOGS_FILE_PREFIX) }
            .sortedBy { it.name }
            .dropLast(keep)
            .forEach { it.delete() }
        return
    }
    val resolver = appContext.context.contentResolver
    val ids = mutableListOf<Long>()
    resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
        arrayOf("$WATCH_LOGS_FILE_PREFIX%"),
        "${MediaStore.Downloads.DISPLAY_NAME} ASC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
        while (cursor.moveToNext()) {
            ids += cursor.getLong(idColumn)
        }
    }
    ids.dropLast(keep).forEach { id ->
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads._ID} = ?",
            arrayOf(id.toString()),
        )
    }
}
