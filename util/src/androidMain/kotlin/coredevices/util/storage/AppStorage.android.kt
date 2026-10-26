package coredevices.util.storage

import io.rebble.libpebblecommon.connection.AppContext
import java.io.File

actual fun AppContext.appStorageBreakdown(): List<StorageUsage> {
    val ctx = context.applicationContext
    return buildList {
        add(StorageUsage("files", ctx.filesDir.sizeRecursive()))
        add(StorageUsage("cache", ctx.cacheDir.sizeRecursive()))
        add(StorageUsage("noBackup", ctx.noBackupFilesDir.sizeRecursive()))
        add(StorageUsage("databases", File(ctx.filesDir.parentFile, "databases").sizeRecursive()))
        ctx.getExternalFilesDir(null)?.let { add(StorageUsage("externalFiles", it.sizeRecursive())) }
        ctx.externalCacheDir?.let { add(StorageUsage("externalCache", it.sizeRecursive())) }
    }
}

private fun File.sizeRecursive(): Long =
    if (!exists()) 0L else walkTopDown().filter { it.isFile }.sumOf { it.length() }
