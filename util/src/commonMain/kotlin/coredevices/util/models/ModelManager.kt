package coredevices.util.models

import coredevices.util.CommonBuildKonfig
import coredevices.util.Platform
import coredevices.util.transcription.CactusModelPathProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

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

    // Downloaded STT models, i.e. everything except the language model (matches getAvailableSTTModels).
    fun getDownloadedSTTModelSlugs(): List<String> {
        return getDownloadedModelSlugs().filter { it != CommonBuildKonfig.CACTUS_LM_MODEL_NAME }
    }

    fun deleteModel(modelName: String) {
        modelPathProvider?.deleteModel(modelName)
    }

    private fun buildModelInfo(slug: String, version: String = weightsVersionFor(slug), intendedTask: String? = null, supportsMultiLanguage: Boolean = true): ModelInfo {
        val sttSizeMB = modelPathProvider?.let {
            val onDisk = (it.getModelSizeBytes(slug) / (1024 * 1024)).toInt()
            if (onDisk > 0) onDisk else KNOWN_STT_SIZE_MB
        } ?: KNOWN_STT_SIZE_MB
        return ModelInfo(
            intendedTask = intendedTask,
            slug = slug,
            sizeInMB = sttSizeMB,
            url = "$HF_BASE/$slug/resolve/$version/${slug.lowercase()}-$QUANTIZATION.zip",
            supportsMultiLanguage = supportsMultiLanguage
        )
    }

    suspend fun getSelectableSTTModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        listOf(
            buildModelInfo(
                slug = CommonBuildKonfig.CACTUS_STT_MODEL,
                intendedTask = "Widest language support",
                supportsMultiLanguage = true
            ),
            buildModelInfo(
                slug = CommonBuildKonfig.CACTUS_STT_MODEL_ENG,
                intendedTask = "Higher accuracy for English",
                supportsMultiLanguage = false
            )
        )
    }

    suspend fun getAvailableSTTModels(): List<ModelInfo> {
        val sttModels = getSelectableSTTModels()

        // Include old downloaded models (e.g. whisper) so they can be deleted
        val lmModel = CommonBuildKonfig.CACTUS_LM_MODEL_NAME
        val oldModels = modelPathProvider?.getDownloadedModels()
            ?.filter { !sttModels.any { m -> m.slug == it } && it != lmModel }
            ?.map { slug ->
                val sizeMB = (modelPathProvider.getModelSizeBytes(slug) / (1024 * 1024)).toInt()
                ModelInfo(slug = slug, sizeInMB = sizeMB)
            } ?: emptyList()

        return sttModels + oldModels
    }

    suspend fun getAvailableLanguageModels(): List<ModelInfo> {
        val lmModel = CommonBuildKonfig.CACTUS_LM_MODEL_NAME
        val version = weightsVersionFor(lmModel)
        val lmSizeMB = modelPathProvider?.let {
            val onDisk = (it.getModelSizeBytes(lmModel) / (1024 * 1024)).toInt()
            if (onDisk > 0) onDisk else KNOWN_LM_SIZE_MB
        } ?: KNOWN_LM_SIZE_MB

        return listOf(ModelInfo(
            slug = lmModel,
            sizeInMB = lmSizeMB,
            url = "$HF_BASE/$lmModel/resolve/$version/${lmModel.lowercase()}-$QUANTIZATION.zip"
        ))
    }

    companion object {
        private const val HF_BASE = "https://huggingface.co/Cactus-Compute"
        private const val QUANTIZATION = "cq4"
        private const val KNOWN_STT_SIZE_MB = 406
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

fun weightsVersionFor(modelSlug: String): String = when (modelSlug) {
    CommonBuildKonfig.CACTUS_STT_MODEL_ENG -> CommonBuildKonfig.CACTUS_WEIGHTS_VERSION_ENG
    else -> CommonBuildKonfig.CACTUS_WEIGHTS_VERSION
}

data class ModelInfo(
    val createdAt: kotlin.time.Instant = kotlin.time.Clock.System.now(),
    val intendedTask: String? = null,
    val slug: String,
    val sizeInMB: Int = 0,
    val url: String = "",
    val supportsMultiLanguage: Boolean = false
)

expect fun Platform.supportsNPU(): Boolean
expect fun Platform.supportsHeavyCPU(): Boolean