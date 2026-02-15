package coredevices.util.models

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

expect class ModelDownloadManager {
    val downloadStatus: StateFlow<ModelDownloadStatus>
    fun downloadSTTModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean
    fun downloadLanguageModel(modelInfo: ModelInfo, allowMetered: Boolean): Boolean
    fun cancelDownload()
}

sealed interface ModelDownloadStatus {
    object Idle : ModelDownloadStatus
    object Cancelled : ModelDownloadStatus
    data class Downloading(val modelSlug: String, val progress: Float? = null) : ModelDownloadStatus
    data class Failed(val modelSlug: String, val errorMessage: String) : ModelDownloadStatus
}