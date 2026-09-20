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
    data class Scheduled(val modelSlug: String) : ModelDownloadStatus
    data class Downloading(val modelSlug: String, val progress: Float? = null) : ModelDownloadStatus
    data class Failed(val modelSlug: String, val errorMessage: String) : ModelDownloadStatus
}

/** Slug of the model being fetched while a download is scheduled or running. */
val ModelDownloadStatus.inProgressSlug: String?
    get() = when (this) {
        is ModelDownloadStatus.Scheduled -> modelSlug
        is ModelDownloadStatus.Downloading -> modelSlug
        else -> null
    }

val ModelDownloadStatus.inProgress: Boolean
    get() = this is ModelDownloadStatus.Scheduled || this is ModelDownloadStatus.Downloading