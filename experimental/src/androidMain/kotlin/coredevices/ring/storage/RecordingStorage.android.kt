package coredevices.ring.storage

import android.content.Context
import android.net.Uri
import dev.gitlive.firebase.storage.File
import kotlinx.io.files.Path
import org.koin.mp.KoinPlatform

internal actual fun getRecordingsCacheDirectory(): Path {
    val context: Context = KoinPlatform.getKoin().get()
    return Path(context.cacheDir.resolve("recordings").absolutePath)
}

internal actual fun getRecordingsDataDirectory(): Path {
    val context: Context = KoinPlatform.getKoin().get()
    return Path(context.filesDir.resolve("recordings").absolutePath)
}

internal actual fun getFirebaseStorageFile(path: Path): File {
    val file = java.io.File(path.toString())
    return File(Uri.fromFile(file))
}