package coredevices.util.transcription

import kotlinx.io.files.Path

sealed class TranscriptionSessionStatus {
    data object Open : TranscriptionSessionStatus()
    data class Transcription(
        val text: String,
        val modelUsed: String? = null,
        /** The configured remote path failed and the local model produced this text. */
        val localFallback: Boolean = false,
    ) : TranscriptionSessionStatus()
    data class Partial(val text: String) : TranscriptionSessionStatus()
}