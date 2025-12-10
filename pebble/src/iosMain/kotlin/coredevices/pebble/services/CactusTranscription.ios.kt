package coredevices.pebble.services

import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual fun tempTranscriptionDirectory(): Path {
    val documentsDir = NSFileManager.defaultManager().URLsForDirectory(
        directory = NSCachesDirectory,
        inDomains = NSUserDomainMask
    ).first() as NSURL
    return Path(documentsDir.path!!, "watch-transcription")
}