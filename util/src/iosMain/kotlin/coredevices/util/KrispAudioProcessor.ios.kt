package coredevices.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual fun loadModelBlob(): ByteArray {
    val path = NSBundle.mainBundle.pathForResource("krisp-bvc-o-pro-v3", ofType = "kef")
        ?: error("Krisp model file not found in app bundle")
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Failed to read Krisp model file")
    return ByteArray(data.length.toInt()).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}