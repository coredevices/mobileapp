package coredevices.ring.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaDataSource
import android.media.MediaPlayer
import co.touchlab.kermit.Logger
import coredevices.util.AudioEncoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.readByteArray
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

actual class AudioPlayer : AutoCloseable, KoinComponent {
    companion object {
        private val logger = Logger.withTag(AudioPlayer::class.simpleName!!)
    }

    actual val playbackState: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.Stopped)
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
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
        released.set(true)
        try {
            audioTrack?.stop()
        } catch (_: Exception) {} // Best-effort stop, we might be here because track is released
        streamJob?.cancel()
        audioTrack?.release()

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

        val newTrack = AudioTrack.Builder()
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
        audioTrack = newTrack
        released.set(false)
        audioManager.requestAudioFocus(
            AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ).build()
        )
        newTrack.play()
        playbackState.value = PlaybackState.Playing(0.0)
        streamJob = scope.launch(Dispatchers.IO) {
            handleRawStream(newTrack, samples, sampleRate, sizeHint.toInt())
        }
    }

    actual fun playAAC(samples: Source, sampleRate: Long) {
        logger.d { "Beginning AAC playback" }
        released.set(true)
        try { audioTrack?.stop() } catch (_: Exception) {}
        streamJob?.cancel()
        audioTrack?.release()
        audioTrack = null
        mediaPlayer?.release()
        mediaPlayer = null

        val bytes = samples.use { it.readByteArray() }
        val player = MediaPlayer()
        mediaPlayer = player
        released.set(false)

        audioManager.requestAudioFocus(
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()
        )
        streamJob = scope.launch(Dispatchers.IO) {
            try {
                player.setDataSource(object : MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position >= bytes.size) return -1
                        val available = (bytes.size - position).toInt()
                        val toRead = minOf(size, available)
                        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + toRead)
                        return toRead
                    }
                    override fun getSize() = bytes.size.toLong()
                    override fun close() {}
                })
                player.prepare()
                player.setVolume(minOf(AUDIO_PLAYER_VOLUME, 1.0f), minOf(AUDIO_PLAYER_VOLUME, 1.0f))
                playbackState.value = PlaybackState.Playing(0.0)
                suspendCancellableCoroutine<Unit> { cont ->
                    player.setOnCompletionListener { cont.resume(Unit) }
                    cont.invokeOnCancellation { runCatching { player.stop() } }
                    player.start()
                }
            } finally {
                runCatching { player.release() }
                if (mediaPlayer === player) mediaPlayer = null
                playbackState.value = PlaybackState.Stopped
            }
        }
    }

    actual fun stop() {
        logger.d { "Stopping" }
        audioTrack?.stop()
        mediaPlayer?.stop()
        streamJob?.cancel()
        playbackState.value = PlaybackState.Stopped
    }

    actual override fun close() {
        logger.d { "Closing" }
        audioTrack?.stop()
        streamJob?.cancel()
        audioTrack?.release()
        mediaPlayer?.release()
        mediaPlayer = null
        released.set(true)
        playbackState.value = PlaybackState.Stopped
    }
}