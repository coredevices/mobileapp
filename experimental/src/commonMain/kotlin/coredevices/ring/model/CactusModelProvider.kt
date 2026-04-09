package coredevices.ring.model

import co.touchlab.kermit.Logger
import com.cactus.Cactus
import coredevices.util.CommonBuildKonfig
import coredevices.util.transcription.CactusModelPathProvider

/**
 * Provides absolute file paths for Cactus models.
 * Handles model download and version management.
 *
 * Models are stored at: <platformModelsDir>/<modelName>/
 * Each model directory contains config.txt, vocab.txt, and .weights files.
 */
expect class CactusModelProvider() : CactusModelPathProvider {
    override suspend fun getSTTModelPath(): String
    override suspend fun getLMModelPath(): String
    override fun isModelDownloaded(modelName: String): Boolean
    override fun getDownloadedModels(): List<String>
    override fun getIncompatibleModels(): List<String>
    override fun deleteModel(modelName: String)
    override fun getModelSizeBytes(modelName: String): Long
    fun setCloudApiKey(key: String)
    override fun initTelemetry()
}
