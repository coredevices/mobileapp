package coredevices.util.models

import coredevices.util.CommonBuildKonfig
import coredevices.util.Platform
import coredevices.util.transcription.CactusModelPathProvider

class ModelManager(
    private val platform: Platform,
    private val modelDownloadManager: ModelDownloadManager,
    private val modelPathProvider: CactusModelPathProvider? = null,
) {
    val modelDownloadStatus = modelDownloadManager.downloadStatus

    fun downloadSTTModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean {
        return modelDownloadManager.downloadSTTModel(modelInfo, allowMetered)
    }

    fun downloadLanguageModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean {
        return modelDownloadManager.downloadLanguageModel(modelInfo, allowMetered)
    }

    fun cancelDownload() {
        modelDownloadManager.cancelDownload()
    }

    fun getDownloadedModelSlugs(): List<String> {
        return modelPathProvider?.getDownloadedModels()
            ?: listOf(CommonBuildKonfig.CACTUS_STT_MODEL, CommonBuildKonfig.CACTUS_LM_MODEL_NAME)
    }

    fun deleteModel(modelName: String) {
        modelPathProvider?.deleteModel(modelName)
    }

    suspend fun getAvailableSTTModels(): List<ModelInfo> {
        val sttModel = CommonBuildKonfig.CACTUS_STT_MODEL
        val sttVersion = CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION
        val sttSizeMB = modelPathProvider?.let {
            val onDisk = (it.getModelSizeBytes(sttModel) / (1024 * 1024)).toInt()
            if (onDisk > 0) onDisk else KNOWN_STT_SIZE_MB
        } ?: KNOWN_STT_SIZE_MB

        val currentModel = ModelInfo(
            slug = sttModel,
            sizeInMB = sttSizeMB,
            url = "$HF_BASE/$sttModel/resolve/$sttVersion/weights/${sttModel.lowercase()}-$QUANTIZATION.zip"
        )

        // Include old downloaded models (e.g. whisper) so they can be deleted
        val lmModel = CommonBuildKonfig.CACTUS_LM_MODEL_NAME
        val oldModels = modelPathProvider?.getDownloadedModels()
            ?.filter { it != sttModel && it != lmModel }
            ?.map { slug ->
                val sizeMB = (modelPathProvider.getModelSizeBytes(slug) / (1024 * 1024)).toInt()
                ModelInfo(slug = slug, sizeInMB = sizeMB)
            } ?: emptyList()

        return listOf(currentModel) + oldModels
    }

    suspend fun getAvailableLanguageModels(): List<ModelInfo> {
        val lmModel = CommonBuildKonfig.CACTUS_LM_MODEL_NAME
        val lmVersion = CommonBuildKonfig.CACTUS_LM_WEIGHTS_VERSION
        val lmSizeMB = modelPathProvider?.let {
            val onDisk = (it.getModelSizeBytes(lmModel) / (1024 * 1024)).toInt()
            if (onDisk > 0) onDisk else KNOWN_LM_SIZE_MB
        } ?: KNOWN_LM_SIZE_MB

        return listOf(ModelInfo(
            slug = lmModel,
            sizeInMB = lmSizeMB,
            url = "$HF_BASE/$lmModel/resolve/$lmVersion/weights/${lmModel.lowercase()}-$QUANTIZATION.zip"
        ))
    }

    companion object {
        private const val HF_BASE = "https://huggingface.co/Cactus-Compute"
        private const val QUANTIZATION = "int8"
        private const val KNOWN_STT_SIZE_MB = 670
        private const val KNOWN_LM_SIZE_MB = 530
    }

    fun getRecommendedSTTMode(): CactusSTTMode {
        return when {
            platform.supportsNPU() || platform.supportsHeavyCPU() -> CactusSTTMode.RemoteFirst
            else -> CactusSTTMode.RemoteOnly
        }
    }

    fun getRecommendedSTTModel(): RecommendedModel {
        return RecommendedModel.Standard(CommonBuildKonfig.CACTUS_STT_MODEL)
    }

    fun getRecommendedLanguageModel(): String {
        return CommonBuildKonfig.CACTUS_LM_MODEL_NAME
    }
}

sealed class RecommendedModel {
    abstract val modelSlug: String
    data class Lite(override val modelSlug: String) : RecommendedModel()
    data class Standard(override val modelSlug: String) : RecommendedModel()
}

data class ModelInfo(
    val createdAt: kotlin.time.Instant = kotlin.time.Clock.System.now(),
    val slug: String,
    val sizeInMB: Int = 0,
    val url: String = ""
)

expect fun Platform.supportsNPU(): Boolean
expect fun Platform.supportsHeavyCPU(): Boolean