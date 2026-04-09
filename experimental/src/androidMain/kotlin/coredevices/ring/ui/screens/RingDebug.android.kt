package coredevices.ring.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.util.writeWavHeader
import kotlinx.coroutines.runInterruptible
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeShortLe
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform
import kotlin.time.Clock

internal actual val BT_PERMISSIONS: List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(android.Manifest.permission.BLUETOOTH_SCAN)
        add(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

internal actual suspend fun storeRecording(samples: ShortArray, sampleRate: Int, collectionIdx: Int): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return runInterruptible {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            storeRecordingLegacy(context, samples, sampleRate, collectionIdx)
        } else {
            storeRecordingMediaStore(context, samples, sampleRate, collectionIdx)
        }
    }
}

private fun storeRecordingLegacy(context: Context, samples: ShortArray, sampleRate: Int, collectionIdx: Int): String {
    if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = java.io.File(path, "ring_recording_${Clock.System.now()}_$collectionIdx.wav")
        file.outputStream().use { outputStream ->
            val buffer = Buffer()
            buffer.writeWavHeader(sampleRate, samples.size * 2)
            samples.forEach {
                buffer.writeShortLe(it)
            }
            outputStream.write(buffer.readByteArray())
        }
        return file.toUri().toString()
    } else {
        throw SecurityException("No permission to write to external storage")
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun storeRecordingMediaStore(context: Context, samples: ShortArray, sampleRate: Int, collectionIdx: Int): String {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "ring_recording_${Clock.System.now()}_$collectionIdx.wav")
        put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    resolver.openOutputStream(uri!!).use { outputStream ->
        val buffer = Buffer()
        buffer.writeWavHeader(sampleRate, samples.size * 2)
        samples.forEach {
            buffer.writeShortLe(it)
        }
        outputStream?.write(buffer.readByteArray())
    }
    return uri.toString()
}

internal actual fun openRecording(path: String): Source {
    val context = KoinPlatform.getKoin().get<Context>()
    val uri = path.toUri()
    return context.contentResolver.openInputStream(uri)!!.asSource().buffered()
}

@Composable
actual fun rememberHaversineSatelliteManager(hwVersion: Pair<Int, Int>): KMPHaversineSatelliteManager {
    return koinInject()
}