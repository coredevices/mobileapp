package coredevices.util

import kotlinx.io.Sink
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString

fun Sink.writeWavHeader(sampleRate: Int, audioSize: Int) {
    val chunkSize = audioSize + 36
    writeString("RIFF")
    writeIntLe(chunkSize)
    writeString("WAVE")
    writeString("fmt ")
    writeIntLe(16) // fmt chunk size
    writeShortLe(1) // PCM format
    writeShortLe(1) // Mono
    writeIntLe(sampleRate) // Sample rate
    writeIntLe(sampleRate * 2) // Byte rate
    writeShortLe(2) // Block align
    writeShortLe(16) // Bits per sample
    writeString("data")
    writeIntLe(audioSize)
}