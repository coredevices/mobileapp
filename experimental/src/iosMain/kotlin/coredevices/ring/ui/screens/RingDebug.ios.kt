package coredevices.ring.ui.screens

import androidx.compose.runtime.Composable
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.util.writeWavHeader
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeShortLe
import org.koin.compose.koinInject
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.time.Clock

internal actual val BT_PERMISSIONS: List<String>
    get() = listOf("bluetooth")

internal actual suspend fun storeRecording(samples: ShortArray, sampleRate: Int, collectionIdx: Int): String {
    val fileManager = NSFileManager.defaultManager
    val documentDirectory = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first()!! as NSURL
    val name = "ring_recording_${Clock.System.now()}_$collectionIdx.wav"
    val file = documentDirectory.URLByAppendingPathComponent(name)!!
    val path = file.path!!
    SystemFileSystem.sink(Path(path)).buffered().use { sink ->
        sink.writeWavHeader(sampleRate, samples.size*2)
        samples.forEach {
            sink.writeShortLe(it)
        }
    }
    return path
}

internal actual fun openRecording(path: String): Source {
    return SystemFileSystem.source(Path(path)).buffered()
}

@Composable
actual fun rememberHaversineSatelliteManager(hwVersion: Pair<Int, Int>): KMPHaversineSatelliteManager {
    return koinInject()
}