package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.packets.MusicControl

public enum class PlaybackState(public val protocolValue: MusicControl.PlaybackState) {
    Playing(MusicControl.PlaybackState.Playing),
    Paused(MusicControl.PlaybackState.Paused),
    Buffering(MusicControl.PlaybackState.Playing),
    Error(MusicControl.PlaybackState.Paused),
}

internal fun PlaybackState.isActive(): Boolean = this == PlaybackState.Playing || this == PlaybackState.Buffering