package coredevices.ring.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import coredevices.util.AudioEncoding
import io.ktor.utils.io.core.writeFully
import kotlinx.io.Buffer
import kotlinx.io.RawSource

actual class AudioRecorder(context: Context): AutoCloseable {
    companion object {
        private const val SAMPLE_RATE = 16000
        private val AUDIO_ENCODING = AudioEncoding.PCM_16BIT
    }
    @SuppressLint("MissingPermission")
    private val audioRecorder = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AUDIO_ENCODING.toAudioFormat())
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
        )
        .setBufferSizeInBytes(1024)
        .build()
    
    @Volatile
    private var isRecording = false
    
    actual suspend fun startRecording(): RawSource {
        isRecording = true
        audioRecorder.startRecording()
        return object: RawSource {
            override fun close() {
                audioRecorder.stop()
                isRecording = false
            }

            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                if (!isRecording) {
                    return -1L // Signal end of stream
                }
                val buffer = ByteArray(byteCount.toInt())
                val bytesRead = audioRecorder.read(buffer, 0, byteCount.toInt())
                if (bytesRead <= 0 || !isRecording) {
                    return -1L // Signal end of stream
                }
                sink.writeFully(buffer, 0, bytesRead)
                return bytesRead.toLong()
            }
        }
    }

    actual suspend fun stopRecording() {
        isRecording = false
        audioRecorder.stop()
    }

    actual override fun close() {
        audioRecorder.release()
    }

    actual val encoding: AudioEncoding
        get() = AUDIO_ENCODING
    actual val sampleRate: Int
        get() = audioRecorder.sampleRate
}