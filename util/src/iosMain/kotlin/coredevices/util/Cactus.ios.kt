package coredevices.util

import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun getModelDirectories(): List<String> {
    val cachesDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory, NSUserDomainMask, true
    ).first() as String
    val voskModelPaths = SystemFileSystem.list(Path(cachesDir))
        .filter { it.name.startsWith("vosk-") }
        .map { it.toString() }
    val whisperModelPaths = try {
        SystemFileSystem.list(Path(cachesDir))
            .filter { it.name.startsWith("whisper-") }
            .map { it.toString() } + SystemFileSystem.list(Path("$cachesDir/models"))
            .filter { it.name.startsWith("whisper-") }
            .map { it.toString() }
    } catch (e: FileNotFoundException) {
        emptyList()
    }
    return listOf(
        "$cachesDir/models/vosk",
        // legacy path
        "$cachesDir/models/vosk",
        "$cachesDir/vosk-model",
    ) + voskModelPaths + whisperModelPaths
}