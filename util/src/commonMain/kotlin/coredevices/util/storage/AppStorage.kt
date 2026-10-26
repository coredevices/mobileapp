package coredevices.util.storage

import io.rebble.libpebblecommon.connection.AppContext

data class StorageUsage(val label: String, val bytes: Long)

/**
 * On-disk size of the app's own container, by top-level directory. Walks the tree, so it blocks —
 * call off the main thread.
 */
expect fun AppContext.appStorageBreakdown(): List<StorageUsage>

fun formatStorageSummary(usage: List<StorageUsage>): String = buildString {
    append("\nStorage usage:")
    usage.sortedByDescending { it.bytes }.forEach {
        append("\n  ${it.label}: ${formatBytes(it.bytes)}")
    }
    append("\n  total: ${formatBytes(usage.sumOf { it.bytes })}")
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${units[unit]}"
}
