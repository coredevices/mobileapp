package io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.PlaybackStateData.Companion.DEFAULT
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.music.MusicAction
import io.rebble.libpebblecommon.music.PlaybackState
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.music.SystemMusicControl
import io.rebble.libpebblecommon.services.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
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
) {
    companion object {
        val DEFAULT = PlaybackStateData(
            state = PlaybackState.Paused,
            trackPosMs = 0u,
            playbackRatePct = 0u,
            shuffle = false,
            repeatType = RepeatType.Off,
        )
    }
}

class MusicControlManager(
    private val watchScope: ConnectionCoroutineScope,
    private val musicControlService: MusicService,
    private val systemMusicControl: SystemMusicControl,
    private val clock: Clock,
    private val watchConfigFlow: WatchConfigFlow
) {
    private val logger = Logger.withTag("MusicControlManager")
    private var lastSentStatus: PlaybackStatus? = null
    private var lastPosition: TimestampedPosition = TimestampedPosition(Instant.DISTANT_PAST, 0, 1.0f)
    private val playbackStateUpdates = MutableStateFlow(DEFAULT)

    fun init() {
        musicControlService.updateRequestTrigger.onEach {
            // Refresh everything
            lastSentStatus = null
            sendChangesToWatch(systemMusicControl.playbackState.value)
        }.launchIn(watchScope)
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
        ) {
            val playbackState = if (watchConfigFlow.value.alwaysSendMusicPaused) {
                PlaybackState.Paused
            } else {
                status?.playbackState ?: PlaybackState.Paused
            }

            playbackStateUpdates.value = PlaybackStateData(
                state = playbackState,
                trackPosMs = status?.playbackPositionMs?.toUInt() ?: 0u,
                playbackRatePct = (status?.playbackRate?.times(100)?.toInt() ?: 0).toUInt(),
                shuffle = status?.shuffle ?: false,
                repeatType = status?.repeat ?: RepeatType.Off,
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
