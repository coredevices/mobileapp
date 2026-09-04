package coredevices.util.transcription

import coredevices.util.CommonBuildKonfig
import coredevices.util.models.weightsVersionFor

interface CactusModelPathProvider {
    suspend fun getSTTModelPath(
        modelName: String = CommonBuildKonfig.CACTUS_STT_MODEL,
        version: String = weightsVersionFor(modelName),
    ): String
    suspend fun getLMModelPath(): String
    fun isModelDownloaded(modelName: String): Boolean
    fun getDownloadedModels(): List<String>
    fun getIncompatibleModels(): List<String>
    fun deleteModel(modelName: String)
    fun getModelSizeBytes(modelName: String): Long
    fun initTelemetry()
}
