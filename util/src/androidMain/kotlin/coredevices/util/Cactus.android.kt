package coredevices.util

import android.content.Context
import org.koin.mp.KoinPlatform

actual fun getModelDirectories(): List<String> {
    val context = KoinPlatform.getKoin().get<Context>()
    val modelFolder = context.filesDir.resolve("models/")
    val voskModelPaths = modelFolder.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("vosk-") }
        ?.map { it.absolutePath }
        ?: emptyList()
    val whisperModelPaths = modelFolder.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("whisper-") }
        ?.map { it.absolutePath }
        ?: emptyList()
    return listOf(
        // Legacy paths
        context.filesDir.resolve("models/vosk").absolutePath,
        context.filesDir.resolve("vosk-model").absolutePath,
    ) + voskModelPaths + whisperModelPaths
}