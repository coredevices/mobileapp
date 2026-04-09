package coredevices.ring.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import co.touchlab.kermit.Logger
import coredevices.util.AudioEncoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.io.IOException
import kotlinx.io.Source
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean

actual class AudioPlayer : AutoCloseable, KoinComponent {
    companion object {
        private val logger = Logger.withTag(AudioPlayer::class.simpleName!!)
    }

    actual val playbackState: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.Stopped)
    private var audioTrack: AudioTrack? = null
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val released = AtomicBoolean(false)
    private val context: Context by inject()
    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private suspend fun handleRawStream(track: AudioTrack, samples: Source, sampleRate: Long, sizeHint: Int = 1) {
        if (released.get()) {
            logger.e { "handleRawStream() when audio track released" }
            return
        }
        val buffer = ByteArray(sampleRate.toInt()*2)
        var bytesTotal = 0
        samples.use {
            while (!samples.exhausted()) {
                val bytesRead = it.readAtMostTo(buffer)
                try {
                    runInterruptible {
                        track.write(buffer, 0, bytesRead)
                    }
                    bytesTotal += bytesRead
                    val percentage = bytesTotal.toDouble() / sizeHint
                    playbackState.value = PlaybackState.Playing(percentage)
                } catch (e: IOException) {
                    logger.w(e) { "AudioTrack closed unexpectedly, released: ${released.get()}" }
                    return@use
                } catch (e: IllegalStateException) {
                    if (e !is CancellationException) {
                        logger.w(e) { "AudioTrack closed unexpectedly, released: ${released.get()}" }
                    } else {
                        throw e
                    }
                    return@use
                }
            }
        }
        try {
            track.stop()
            playbackState.value = PlaybackState.Stopped
        } catch (_: Exception) {} // Best-effort stop, we might be here because track is released
    }

    actual fun playRaw(
        samples: Source,
        sampleRate: Long,
        encoding: AudioEncoding,
        sizeHint: Long
    ) {
        logger.d { "Beginning playback" }
        audioTrack?.release()
        released.set(true)
        streamJob?.cancel()

        var bufferSize = AudioTrack.getMinBufferSize(sampleRate.toInt(), AudioFormat.CHANNEL_OUT_MONO, encoding.toAudioFormat())
        if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
            logger.w { "Couldn't obtain buffer size for track, using a safe guess (code: $bufferSize)" }
            bufferSize = sampleRate.toInt()*2
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(encoding.toAudioFormat())
                    .setSampleRate(sampleRate.toInt())
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
            .apply {
                setVolume(AUDIO_PLAYER_VOLUME)
            }
        released.set(false)
        audioManager.requestAudioFocus(
            AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ).build()
        )
        streamJob = scope.launch(Dispatchers.IO) {
            handleRawStream(audioTrack!!, samples, sampleRate, sizeHint.toInt())
        }
        playbackState.value = PlaybackState.Playing(0.0)
        audioTrack?.play()
    }

    actual fun stop() {
        logger.d { "Stopping" }
        audioTrack?.stop()
        streamJob?.cancel()
        playbackState.value = PlaybackState.Stopped
    }

    actual override fun close() {
        logger.d { "Closing" }
        audioTrack?.stop()
        streamJob?.cancel()
        audioTrack?.release()
        released.set(true)
        playbackState.value = PlaybackState.Stopped
    }
}