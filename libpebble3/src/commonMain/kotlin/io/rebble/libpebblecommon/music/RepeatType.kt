package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.packets.MusicControl

public enum class RepeatType(public val protocolValue: MusicControl.RepeatState) {
    Off(MusicControl.RepeatState.Off),
    One(MusicControl.RepeatState.One),
    All(MusicControl.RepeatState.All)
}