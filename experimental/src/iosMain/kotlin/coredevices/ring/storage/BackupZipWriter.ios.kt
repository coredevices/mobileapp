@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package coredevices.ring.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.files.Path
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.appendBytes
import platform.Foundation.create
import platform.Foundation.writeToFile

actual class BackupZipWriter actual constructor(private val outputPath: Path) {
    // iOS doesn't have built-in zip creation in Foundation without third-party libs.
    // We'll write files individually to a directory and skip zip on iOS for now.
    // TODO: Add proper zip support via libcompression or a KMP zip library.
    private val entries = mutableListOf<Pair<String, ByteArray>>()

    actual fun addEntry(name: String, data: ByteArray) {
        entries.add(name to data)
    }

    actual fun close() {
        // Write each entry as a separate file under the output path (treated as directory)
        val dirPath = outputPath.toString().removeSuffix(".zip")
        platform.Foundation.NSFileManager.defaultManager.createDirectoryAtPath(
            dirPath, withIntermediateDirectories = true, attributes = null, error = null
        )
        for ((name, data) in entries) {
            val filePath = "$dirPath/$name"
            val parentDir = filePath.substringBeforeLast("/")
            platform.Foundation.NSFileManager.defaultManager.createDirectoryAtPath(
                parentDir, withIntermediateDirectories = true, attributes = null, error = null
            )
            data.usePinned { pinned ->
                val nsData = NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
                nsData.writeToFile(filePath, atomically = true)
            }
        }
    }
}
