package coredevices.pebble

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

internal const val WATCH_LOGS_FOLDER_NAME = "Pebble watch logs"
internal const val WATCH_LOGS_FILE_PREFIX = "watch-logs-"

/**
 * Copies [source] into shared storage the user can browse, returning a description of where it
 * landed, or null if it could not be saved.
 */
expect fun saveWatchLogCopy(appContext: AppContext, source: Path, fileName: String): String?

/** Deletes all but the [keep] newest saved copies. */
expect fun pruneWatchLogCopies(appContext: AppContext, keep: Int)
