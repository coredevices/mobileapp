package io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.PlaybackStateData.Companion.DEFAULT
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.imaging.ImagingService
import io.rebble.libpebblecommon.music.MusicAction
import io.rebble.libpebblecommon.music.PlaybackState
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.music.SystemMusicControl
import io.rebble.libpebblecommon.music.matchesTruncated
import io.rebble.libpebblecommon.packets.Imaging
import io.rebble.libpebblecommon.packets.MusicControl
import io.rebble.libpebblecommon.services.MusicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class TimestampedPosition(
    val timestamp: Instant,
    val positionMs: Long,
    val rate: Float,
)

data class PlaybackStateData(
    val state: PlaybackState,
    val trackPosMs: UInt,
    val playbackRatePct: UInt,
    val shuffle: Boolean,
    val repeatType: RepeatType,
    val skipSeeksWithinTrack: Boolean,
) {
    companion object {
        val DEFAULT = PlaybackStateData(
            state = PlaybackState.Paused,
            trackPosMs = 0u,
            playbackRatePct = 0u,
            shuffle = false,
            repeatType = RepeatType.Off,
            skipSeeksWithinTrack = false,
        )
    }
}

class MusicControlManager(
    private val watchScope: ConnectionCoroutineScope,
    private val musicControlService: MusicService,
    private val imagingService: ImagingService,
    private val systemMusicControl: SystemMusicControl,
    private val clock: Clock,
    private val watchConfigFlow: WatchConfigFlow
) {
    private val logger = Logger.withTag("MusicControlManager")
    private var lastSentStatus: PlaybackStatus? = null
    private var lastPosition: TimestampedPosition = TimestampedPosition(Instant.DISTANT_PAST, 0, 1.0f)
    private val playbackStateUpdates = MutableSharedFlow<PlaybackStateData>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .also { it.tryEmit(DEFAULT) }

    // An album-art request whose track matched but had no artwork yet, kept so we can answer it once
    // the platform reports late artwork (see systemMusicControl.albumArtUpdated). Null when nothing
    // is outstanding.
    private var pendingAlbumArt: Imaging.AlbumArtRequest? = null

    fun init() {
        musicControlService.updateRequestTrigger.onEach {
            // Refresh everything
            lastSentStatus = null
            sendChangesToWatch(systemMusicControl.playbackState.value)
        }.launchIn(watchScope)
        if (systemMusicControl.supportsAlbumArt) {
            imagingService.registerHandler(Imaging.ImageType.AlbumArt, ::albumArtFor)
            systemMusicControl.albumArtUpdated.onEach {
                // Late artwork arrived; answer the request we couldn't fill, if it's still relevant.
                val pending = pendingAlbumArt ?: return@onEach
                val art = albumArtFor(pending) ?: return@onEach
                imagingService.serveImage(pending.token.get(), Imaging.ImageType.AlbumArt, art)
            }.launchIn(watchScope)
        }
        systemMusicControl.playbackState.onEach { state ->
            sendChangesToWatch(state)
        }.launchIn(watchScope)
        playbackStateUpdates.sample(1.seconds).onEach {
            musicControlService.updatePlaybackState(
                state = it.state,
                trackPosMs = it.trackPosMs,
                playbackRatePct = it.playbackRatePct,
                shuffle = it.shuffle,
                repeatType = it.repeatType,
                skipSeeksWithinTrack = it.skipSeeksWithinTrack,
            )
        }.launchIn(watchScope)

        musicControlService.musicActions.onEach {
            logger.d { "Received music action: $it" }
            when (it) {
                MusicAction.Play -> systemMusicControl.play()
                MusicAction.Pause -> systemMusicControl.pause()
                MusicAction.PlayPause -> systemMusicControl.playPause()
                MusicAction.NextTrack -> systemMusicControl.nextTrack()
                MusicAction.PreviousTrack -> systemMusicControl.previousTrack()
                MusicAction.VolumeDown -> systemMusicControl.volumeDown()
                MusicAction.VolumeUp -> systemMusicControl.volumeUp()
            }
        }.launchIn(watchScope)
    }

    // Album art for [request]. Remembers it when the track matched but artwork isn't ready yet, so a
    // later albumArtUpdated can fill it. An encoder failure is deterministic: don't retry that.
    private suspend fun albumArtFor(request: Imaging.Request): EncodedImage? {
        if (request !is Imaging.AlbumArtRequest) return null
        val title = request.title.get()
        val artist = request.artist.get()
        val art = try {
            if (matchesCurrentTrack(title, artist)) {
                systemMusicControl.getAlbumArt(
                    title = title,
                    artist = artist,
                    width = request.width.get().toInt(),
                    height = request.height.get().toInt(),
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "album art failed for token=${request.token.get()}" }
            pendingAlbumArt = null
            return null
        }
        pendingAlbumArt = request.takeIf { art == null && matchesCurrentTrack(title, artist) }
        return art
    }

    // True if [title]/[artist] name the track that's playing. An empty request string must not match
    // everything, so require at least one non-empty field.
    private fun matchesCurrentTrack(title: String, artist: String): Boolean {
        if (title.isEmpty() && artist.isEmpty()) return false
        val current = systemMusicControl.playbackState.value?.currentTrack ?: return false
        return matchesTruncated(current.title, title) && matchesTruncated(current.artist, artist)
    }

    private fun hasPositionChanged(status: PlaybackStatus?): Boolean {
        val positionMs = status?.playbackPositionMs ?: 0
        val now = clock.now()
        val diffMs = (now - lastPosition.timestamp).inWholeMilliseconds.toFloat().let {
            // Seriously... 0.0 playback rate?
            if (it == 0f) 1f else it
        }
        val expectedPositionMs = lastPosition.positionMs + (lastPosition.rate * diffMs).toLong()
        val differenceMs = abs(positionMs - expectedPositionMs)
        val changedEnough = differenceMs.milliseconds > POSITION_CHANGE_THRESHOLD
        if (changedEnough) {
            logger.v { "hasPositionChanged: $differenceMs ms (status = $status)" }
        }
        return changedEnough
    }

    private suspend fun sendChangesToWatch(status: PlaybackStatus?) {
        if (lastSentStatus?.playerInfo != status?.playerInfo) {
            musicControlService.updatePlayerInfo(
                packageId = status?.playerInfo?.packageId ?: "",
                name = status?.playerInfo?.name ?: "",
            )
        }
        if (lastSentStatus?.playbackState != status?.playbackState
            || hasPositionChanged(status)
            || lastSentStatus?.playbackRate != status?.playbackRate
            || lastSentStatus?.shuffle != status?.shuffle
            || lastSentStatus?.repeat != status?.repeat
            || lastSentStatus?.skipSeeksWithinTrack != status?.skipSeeksWithinTrack
        ) {
            val playbackState = if (watchConfigFlow.value.alwaysSendMusicPaused) {
                PlaybackState.Paused
            } else {
                status?.playbackState ?: PlaybackState.Paused
            }

            playbackStateUpdates.emit(
                PlaybackStateData(
                    state = playbackState,
                    trackPosMs = status?.playbackPositionMs?.toUInt() ?: 0u,
                    playbackRatePct = (status?.playbackRate?.times(100)?.toInt() ?: 0).toUInt(),
                    shuffle = status?.shuffle ?: false,
                    repeatType = status?.repeat ?: RepeatType.Off,
                    skipSeeksWithinTrack = status?.skipSeeksWithinTrack ?: false,
                )
            )
            lastPosition = TimestampedPosition(
                timestamp = clock.now(),
                positionMs = status?.playbackPositionMs ?: 0,
                rate = status?.playbackRate ?: 1.0f,
            )
        }
        if (lastSentStatus?.volume != status?.volume) {
            musicControlService.updateVolumeInfo(
                volumePercent = (status?.volume ?: 0).toUByte()
            )
        }
        if (lastSentStatus?.currentTrack != status?.currentTrack) {
            musicControlService.updateTrack(
                track = status?.currentTrack ?: MusicTrack(
                    title = "",
                    artist = "",
                    album = "",
                    length = Duration.ZERO
                )
            )
        }
        lastSentStatus = status
    }

    companion object {
        private val POSITION_CHANGE_THRESHOLD = 3.seconds
    }
}

