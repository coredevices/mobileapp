package coredevices.util.models

import com.cactus.CactusLM
import com.cactus.CactusModelManager
import com.cactus.CactusSTT
import coredevices.util.Platform
import kotlin.time.Instant

class ModelManager(
    private val platform: Platform,
    private val modelDownloadManager: ModelDownloadManager,
) {
    companion object {
        private val SUPPORTED_STT_PREFIXES = setOf(
            "whisper"
        )
        private val SUPPORTED_LM_PREFIXES = setOf(
            "qwen"
        )
        private const val MAX_MODEL_SIZE_MB = 1024
    }
    val modelDownloadStatus = modelDownloadManager.downloadStatus
    fun deleteModel(modelName: String) {
        CactusModelManager.deleteModel(modelName)
    }

    fun getDownloadedModelSlugs(): List<String> {
        return CactusModelManager.getDownloadedModels()
    }

    /**
     * Downloads the specified STT model.
     * @param modelInfo Information about the model to download.
     * @param allowMetered Whether to allow downloading over metered connections.
     * @return True if the download was initiated successfully, false otherwise.
     */
    fun downloadSTTModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean {
        return modelDownloadManager.downloadSTTModel(modelInfo, allowMetered)
    }

    /**
     * Downloads the specified language model.
     * @param modelInfo Information about the model to download.
     * @param allowMetered Whether to allow downloading over metered connections.
     * @return True if the download was initiated successfully, false otherwise.
     */
    fun downloadLanguageModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean {
        return modelDownloadManager.downloadLanguageModel(modelInfo, allowMetered)
    }

    fun cancelDownload() {
        modelDownloadManager.cancelDownload()
    }

    suspend fun getAvailableSTTModels(): List<ModelInfo> {
        return CactusSTT().getVoiceModels().map {
            ModelInfo(
                createdAt = Instant.parse(it.created_at),
                slug = it.slug,
                sizeInMB = it.size_mb,
                url = it.download_url
            )
        }.filter {
            SUPPORTED_STT_PREFIXES.any { prefix -> it.slug.startsWith(prefix, ignoreCase = true) }
                    && it.sizeInMB <= MAX_MODEL_SIZE_MB
        }
    }

    suspend fun getAvailableLanguageModels(): List<ModelInfo> {
        return CactusLM().getModels().map {
            ModelInfo(
                createdAt = Instant.parse(it.created_at),
                slug = it.slug,
                sizeInMB = it.size_mb,
                url = it.download_url
            )
        }.filter {
            SUPPORTED_LM_PREFIXES.any { prefix -> it.slug.startsWith(prefix, ignoreCase = true) }
        }
    }

    fun getRecommendedSTTMode(): CactusSTTMode {
        // Implementation for determining the recommended STT mode
        return when {
            platform.supportsNPU() || platform.supportsHeavyCPU() -> CactusSTTMode.RemoteFirst
            else -> CactusSTTMode.RemoteOnly
        }
    }

    fun getRecommendedSTTModel(): String {
        return when {
            platform.supportsNPU() -> "whisper-medium-pro"
            platform.supportsHeavyCPU() -> "whisper-small"
            else -> "whisper-base"
        }
    }

    fun getRecommendedLanguageModel(): String {
        return "qwen3-0.6"
    }
}

data class ModelInfo(
    val createdAt: Instant,
    val slug: String,
    val sizeInMB: Int,
    val url: String
)

expect fun Platform.supportsNPU(): Boolean
expect fun Platform.supportsHeavyCPU(): Boolean