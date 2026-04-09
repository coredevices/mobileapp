package coredevices.ring.util

import coredevices.util.AudioEncoding
import kotlinx.io.RawSource

expect class AudioRecorder: AutoCloseable {
    val encoding: AudioEncoding
    val sampleRate: Int
    suspend fun startRecording(): RawSource
    suspend fun stopRecording()
    override fun close()
}