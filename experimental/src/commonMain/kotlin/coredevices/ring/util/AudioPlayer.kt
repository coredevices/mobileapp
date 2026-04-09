package coredevices.ring.util

import coredevices.util.AudioEncoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Source

internal const val AUDIO_PLAYER_VOLUME = 1.7f

expect class AudioPlayer(): AutoCloseable {
    val playbackState: MutableStateFlow<PlaybackState>
    fun playRaw(samples: Source, sampleRate: Long, encoding: AudioEncoding, sizeHint: Long = 1)
    fun stop()
    override fun close()
}

sealed class PlaybackState {
    data class Playing(val percentageComplete: Double): PlaybackState()
    data object Stopped: PlaybackState()
}