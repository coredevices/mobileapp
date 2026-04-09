package coredevices.ring.util

import coredevices.util.AudioEncoding
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.readShortLe
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.Foundation.NSError
import platform.posix.memcpy
import kotlin.coroutines.resume

fun AudioEncoding.toAVAudioFormat(sampleRate: Int, channels: Int = 1) = when (this) {
    AudioEncoding.PCM_16BIT -> AVAudioFormat(
        AVAudioPCMFormatInt16,
        sampleRate.toDouble(),
        channels.toUInt(),
        false
    )
    AudioEncoding.PCM_FLOAT_32BIT -> AVAudioFormat(
        AVAudioPCMFormatFloat32,
        sampleRate.toDouble(),
        channels.toUInt(),
        false
    )
}

actual class AudioPlayer actual constructor() : AutoCloseable {
    actual val playbackState: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.Stopped)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var playJob: Job? = null

    actual fun playRaw(
        samples: Source,
        sampleRate: Long,
        encoding: AudioEncoding,
        sizeHint: Long
    ) {
        val sampleSizeHint = when (encoding) {
            AudioEncoding.PCM_16BIT -> sizeHint / 2
            AudioEncoding.PCM_FLOAT_32BIT -> sizeHint / 4
        }
        playJob?.cancel()
        val avAudioEngine = AVAudioEngine()
        val avAudioPlayerNode = AVAudioPlayerNode()
        val avFormat = AudioEncoding.PCM_FLOAT_32BIT.toAVAudioFormat(sampleRate.toInt()) // Force float format as iOS doesn't like int16
        playJob = scope.launch {
            var playbackProgress = 0
            withContext(Dispatchers.Main) {
                avAudioEngine.attachNode(avAudioPlayerNode)
                avAudioEngine.connect(avAudioPlayerNode, avAudioEngine.outputNode, avFormat)
                avAudioEngine.prepare()
                val (result, error) = memScoped {
                    val error = allocPointerTo<ObjCObjectVar<NSError?>>()
                    val result = avAudioEngine.startAndReturnError(error.value)
                    result to error.value?.pointed?.value
                }
                if (!result || error != null) {
                    error("Failed to start audio engine: ${error?.localizedDescription}")
                }
                avAudioPlayerNode.setVolume(AUDIO_PLAYER_VOLUME)
                avAudioPlayerNode.play()
            }
            val buffer = AVAudioPCMBuffer(
                avFormat,
                sampleSizeHint.coerceIn(1024, 4096).toUInt()
            )
            val data = buffer.floatChannelData!![0]!!
            playbackState.value = PlaybackState.Playing(0.0)

            val divBuffer = FloatArray(buffer.frameCapacity.toInt())
            while (!samples.exhausted()) {
                var samplesRead = 0
                while (samplesRead < divBuffer.size && !samples.exhausted()) {
                    val short = samples.readShortLe()
                    divBuffer[samplesRead] = short.toFloat() / Short.MAX_VALUE
                    samplesRead++
                }
                val ptr = divBuffer.refTo(0)
                memcpy(data, ptr, samplesRead.toULong() * 4u)
                buffer.frameLength = samplesRead.toUInt()
                suspendCancellableCoroutine<Unit> { continuation ->
                    avAudioPlayerNode.scheduleBuffer(buffer) {
                        continuation.resume(Unit)
                    }
                }
                playbackProgress += samplesRead
                playbackState.value = PlaybackState.Playing(playbackProgress.toDouble() / sampleSizeHint)
            }
        }.also {
            it.invokeOnCompletion {
                avAudioPlayerNode.stop()
                avAudioEngine.stop()
                playbackState.value = PlaybackState.Stopped
            }
        }
    }

    actual fun stop() {
        playJob?.cancel()
    }

    actual override fun close() {
        stop()
    }
}
