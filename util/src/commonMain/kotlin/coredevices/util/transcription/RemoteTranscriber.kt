package coredevices.util.transcription

import coredevices.util.CloudSTTProvider

/**
 * A cloud STT backend selectable via [coredevices.util.STTConfig.cloudProvider]. Each implementation
 * owns its availability check and its own timeout/backup policy, keyed by [provider]. Adding a
 * backend is a new implementation plus its registration; mode routing stays unchanged.
 */
interface RemoteTranscriber {
    val provider: CloudSTTProvider

    suspend fun isAvailable(): Boolean

    /**
     * @param willFallbackLocal the caller has a local model to fall back on, so tighten the timeout
     * and skip internal backups rather than spend the caller's whole time budget.
     */
    suspend fun transcribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        willFallbackLocal: Boolean,
    ): TranscriptionSessionStatus.Transcription
}
