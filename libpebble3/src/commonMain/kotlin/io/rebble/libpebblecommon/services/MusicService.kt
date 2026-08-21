package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.toPacket
import io.rebble.libpebblecommon.music.MusicAction
import io.rebble.libpebblecommon.music.PlaybackState
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.packets.MusicControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull

class MusicService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService, ConnectedPebble.Music {
    private val logger = Logger.withTag("MusicService")
    override val musicActions = protocolHandler.inboundMessages
        .filterIsInstance<MusicControl>()
        .mapNotNull {
            when (it.message) {
                MusicControl.Message.Play -> MusicAction.Play
                MusicControl.Message.Pause -> MusicAction.Pause
                MusicControl.Message.PlayPause -> MusicAction.PlayPause
                MusicControl.Message.NextTrack -> MusicAction.NextTrack
                MusicControl.Message.PreviousTrack -> MusicAction.PreviousTrack
                MusicControl.Message.VolumeDown -> MusicAction.VolumeDown
                MusicControl.Message.VolumeUp -> MusicAction.VolumeUp
                else -> null
            }
        }

    override val updateRequestTrigger: Flow<Unit> = protocolHandler.inboundMessages
        .filterIsInstance<MusicControl>()
        .mapNotNull {
            if (it.message == MusicControl.Message.GetCurrentTrack) {
                logger.d { "Received GetCurrentTrack request, triggering update" }
                Unit
            } else {
                null
            }
        }

    override suspend fun updatePlayerInfo(packageId: String, name: String) {
        send(
            MusicControl.UpdatePlayerInfo(
                pkg = packageId,
                name = name
            )
        )
    }

    override suspend fun updateTrack(track: MusicTrack) {
        send(track.toPacket())
    }

    override suspend fun updatePlaybackState(
        state: PlaybackState,
        trackPosMs: UInt,
        playbackRatePct: UInt,
        shuffle: Boolean,
        repeatType: RepeatType,
        skipSeeksWithinTrack: Boolean,
    ) {
        send(MusicControl.UpdatePlayStateInfo(
            playbackState = state.protocolValue,
            trackPosition = trackPosMs,
            playRate = playbackRatePct,
            shuffle = if (shuffle) MusicControl.ShuffleState.On else MusicControl.ShuffleState.Off,
            repeat = repeatType.protocolValue,
            skipSeeksWithinTrack = skipSeeksWithinTrack,
        ))
    }

    override suspend fun updateVolumeInfo(volumePercent: UByte) {
        send(MusicControl.UpdateVolumeInfo(volumePercent))
    }

    suspend fun send(packet: MusicControl) {
        protocolHandler.send(packet)
    }
}