package io.rebble.libpebblecommon.voice

import io.rebble.libpebblecommon.packets.Result

public sealed class TranscriptionResult {
    public data class Success(
        val words: List<TranscriptionWord>,
    ) : TranscriptionResult()

    public data object Failed : TranscriptionResult()

    public data class ConnectionError(val message: String) : TranscriptionResult()
    public data class Error(val message: String) : TranscriptionResult()
    public data object Disabled : TranscriptionResult()
}

internal fun TranscriptionResult.toProtocol(): Result {
    return when (this) {
        is TranscriptionResult.Success -> Result.Success
        TranscriptionResult.Failed, is TranscriptionResult.Error -> Result.FailRecognizerError
        is TranscriptionResult.ConnectionError -> Result.FailServiceUnavailable
        TranscriptionResult.Disabled -> Result.FailDisabled
    }
}