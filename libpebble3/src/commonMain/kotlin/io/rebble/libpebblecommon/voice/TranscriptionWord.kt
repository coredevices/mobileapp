package io.rebble.libpebblecommon.voice

import io.rebble.libpebblecommon.packets.Word

public data class TranscriptionWord (
    val word: String,
    val confidence: Float
)

internal fun TranscriptionWord.toProtocol(): Word {
    return Word((confidence * 255).toInt().toUByte(), word)
}