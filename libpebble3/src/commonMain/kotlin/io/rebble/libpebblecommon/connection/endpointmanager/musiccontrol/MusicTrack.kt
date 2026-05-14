package io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol

import io.rebble.libpebblecommon.packets.MusicControl
import kotlin.time.Duration

private val MAX_LENGTH = 64

data class MusicTrack(
    val title: String?,
    val artist: String?,
    val album: String?,
    val length: Duration,
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
)

fun MusicTrack.toPacket(): MusicControl.UpdateCurrentTrack {
    return MusicControl.UpdateCurrentTrack(
        title = title?.take(MAX_LENGTH) ?: "",
        artist = artist?.take(MAX_LENGTH) ?: "",
        album = album?.take(MAX_LENGTH) ?: "",
        trackLength = length.inWholeMilliseconds.toInt(),
        currentTrack = trackNumber,
        trackCount = totalTracks
    )
}
