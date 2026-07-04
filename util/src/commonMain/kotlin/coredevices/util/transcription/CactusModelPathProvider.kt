package coredevices.util.transcription

interface CactusModelPathProvider {
    /** Resolves (downloading if needed) the on-disk path for the given STT model slug. */
    suspend fun getSTTModelPath(modelSlug: String): String
    suspend fun getLMModelPath(): String
    fun isModelDownloaded(modelName: String): Boolean
    fun getDownloadedModels(): List<String>
    fun getIncompatibleModels(): List<String>
    fun deleteModel(modelName: String)
    fun getModelSizeBytes(modelName: String): Long
    fun initTelemetry()
}
