package coredevices.ring.storage

import dev.gitlive.firebase.storage.File
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual fun getRecordingsCacheDirectory(): Path {
    val fm = NSFileManager.defaultManager
    val path = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first()!! as NSURL
    return Path(path.path!!)
}

internal actual fun getRecordingsDataDirectory(): Path {
    val fm = NSFileManager.defaultManager
    val path = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first()!! as NSURL
    return Path(path.path!!)
}

internal actual fun getFirebaseStorageFile(path: Path): File = File(NSURL.fileURLWithPath(path.toString()))