package coredevices.util.transcription

import kotlinx.io.files.Path

sealed class TranscriptionSessionStatus {
    data object Open : TranscriptionSessionStatus()
    data class Transcription(val text: String, val modelUsed: String? = null) : TranscriptionSessionStatus()
    data class Partial(val text: String) : TranscriptionSessionStatus()
}