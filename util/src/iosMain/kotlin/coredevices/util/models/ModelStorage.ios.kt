package coredevices.util.models

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

private const val MODELS_DIR_NAME = "models"

/**
 * Where model weights live on iOS. Everything that reads or writes them must resolve the
 * location through here, or a download lands somewhere the readiness check never looks.
 *
 * Application Support, not Caches: iOS purges Caches under storage pressure.
 */
fun modelsDirectory(): String = resolveModelsDirectory(
    appSupportDir = systemDirectory(NSApplicationSupportDirectory),
    cachesDir = systemDirectory(NSCachesDirectory),
)

private fun systemDirectory(directory: ULong): String =
    NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String

internal fun resolveModelsDirectory(appSupportDir: String, cachesDir: String): String {
    val fileManager = NSFileManager.defaultManager
    val modelsDir = "$appSupportDir/$MODELS_DIR_NAME"

    // Application Support isn't guaranteed to exist.
    fileManager.createDirectoryAtPath(
        modelsDir, withIntermediateDirectories = true, attributes = null, error = null
    )

    val legacyDir = "$cachesDir/$MODELS_DIR_NAME"
    if (fileManager.fileExistsAtPath(legacyDir)) {
        migrateLegacyModels(fileManager, legacyDir, modelsDir)
    }

    NSURL.fileURLWithPath(modelsDir).setResourceValue(
        true, forKey = NSURLIsExcludedFromBackupKey, error = null
    )
    return modelsDir
}

/**
 * Moves models out of the old Caches location. Per-model rather than a wholesale directory
 * move, because the destination may already exist (possibly empty) — a directory-level move
 * would silently skip, stranding weights that are expensive to re-fetch.
 */
private fun migrateLegacyModels(fileManager: NSFileManager, legacyDir: String, modelsDir: String) {
    val entries = fileManager.contentsOfDirectoryAtPath(legacyDir, null)
        ?.filterIsInstance<String>()
        .orEmpty()

    for (name in entries) {
        val source = "$legacyDir/$name"
        val destination = "$modelsDir/$name"
        if (fileManager.fileExistsAtPath(destination)) {
            // Already migrated (or re-downloaded); the legacy copy is redundant.
            fileManager.removeItemAtPath(source, null)
        } else {
            fileManager.moveItemAtPath(source, toPath = destination, error = null)
        }
    }

    // Only drop the legacy directory once it's empty; removeItemAtPath is recursive, and a
    // move that failed above must not take the weights with it.
    val remaining = fileManager.contentsOfDirectoryAtPath(legacyDir, null).orEmpty()
    if (remaining.isEmpty()) {
        fileManager.removeItemAtPath(legacyDir, null)
    }
}