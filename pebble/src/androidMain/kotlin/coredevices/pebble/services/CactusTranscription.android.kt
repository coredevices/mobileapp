package coredevices.pebble.services

import android.content.Context
import kotlinx.io.files.Path
import org.koin.mp.KoinPlatform

internal actual fun tempTranscriptionDirectory(): Path {
    val context: Context = KoinPlatform.getKoin().get()
    return Path(context.cacheDir.path, "watch-transcription")
}