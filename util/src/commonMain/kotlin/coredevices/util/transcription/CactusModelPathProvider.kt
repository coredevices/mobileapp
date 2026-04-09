package coredevices.util.transcription

interface CactusModelPathProvider {
    suspend fun getSTTModelPath(): String
    suspend fun getLMModelPath(): String
    fun isModelDownloaded(modelName: String): Boolean
    fun getDownloadedModels(): List<String>
    fun getIncompatibleModels(): List<String>
    fun deleteModel(modelName: String)
    fun getModelSizeBytes(modelName: String): Long
    fun initTelemetry()
}
