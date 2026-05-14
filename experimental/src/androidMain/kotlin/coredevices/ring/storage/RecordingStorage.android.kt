package coredevices.ring.storage

import android.content.Context
import android.net.Uri
import dev.gitlive.firebase.storage.File
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.mp.KoinPlatform

internal actual fun getRecordingsCacheDirectory(): Path {
    val context: Context = KoinPlatform.getKoin().get()
    val path = Path(context.cacheDir.resolve("recordings").absolutePath)
    SystemFileSystem.createDirectories(path, false)
    return path
}

internal actual fun getRecordingsDataDirectory(): Path {
    val context: Context = KoinPlatform.getKoin().get()
    return Path(context.filesDir.resolve("recordings").absolutePath)
}

actual fun getFirebaseStorageFile(path: Path): File {
    val file = java.io.File(path.toString())
    return File(Uri.fromFile(file))
}