package coredevices.util

import android.content.Context
import android.os.Build
import co.touchlab.kermit.Logger
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

private val HEAVY_MODELS = listOf(
    "Tensor G4",
    "SM8750", // Snapdragon 8 Elite
    "SM8850", // Snapdragon 8 Elite Gen 5
)

actual fun calculateDefaultSTTModel(): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return CommonBuildKonfig.CACTUS_DEFAULT_STT_MODEL_ANDROID
    }
    val soc = Build.SOC_MODEL
    return if (HEAVY_MODELS.any { soc.contains(it, ignoreCase = true) }) {
        Logger.d("calculateDefaultSTTModel") { "Using heavy STT model for SOC: $soc" }
        CommonBuildKonfig.CACTUS_DEFAULT_STT_MODEL_ANDROID_HEAVY
    } else {
        Logger.d("calculateDefaultSTTModel") { "Using normal STT model for SOC: $soc" }
        CommonBuildKonfig.CACTUS_DEFAULT_STT_MODEL_ANDROID
    }
}