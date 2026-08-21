package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.imaging.EncodedImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SystemMusicControl {
    fun play()
    fun pause()
    fun playPause()
    fun nextTrack()
    fun previousTrack()
    fun volumeDown()
    fun volumeUp()
    val playbackState: StateFlow<PlaybackStatus?>

    /** Whether this platform can supply album art at all. */
    val supportsAlbumArt: Boolean

    /**
     * Render the album art for [title]/[artist] at [width] x [height], or null if that isn't what's
     * playing any more or there's no art for it. [title]/[artist] come from the watch and are
     * matched with [matchesTruncated].
     */
    suspend fun getAlbumArt(title: String, artist: String, width: Int, height: Int): EncodedImage?

    /**
     * Emits when the current track's artwork may have changed without the track itself changing —
     * e.g. Android media sessions often publish title/artist first and add the bitmap in a later
     * callback. Lets a consumer retry [getAlbumArt] after an initial null. Platforms without late
     * artwork return an empty flow.
     */
    val albumArtUpdated: Flow<Unit>
}

/**
 * The watch's copy of a track name is truncated to a fixed byte length, which can cut a multi-byte
 * character in half and decode as U+FFFD, so only compare up to the first replacement character.
 */
fun matchesTruncated(full: String?, truncated: String): Boolean =
    full.orEmpty().startsWith(truncated.substringBefore('\uFFFD'))

data class PlayerInfo(
    val packageId: String,
    val name: String,
)

data class PlaybackStatus(
    val playerInfo: PlayerInfo?,
    val playbackState: PlaybackState,
    val currentTrack: MusicTrack? = null,
    val playbackPositionMs: Long, // Position in milliseconds
    val playbackRate: Float, // Playback rate, 1.0 is normal speed
    val shuffle: Boolean,
    val repeat: RepeatType,
    val volume: Int,
    /**
     * Whether [SystemMusicControl.nextTrack] / [SystemMusicControl.previousTrack] will seek within
     * the current track (podcasts, audiobooks) instead of changing track. Sent to the watch so its
     * music app can show fast-forward/rewind icons.
     */
    val skipSeeksWithinTrack: Boolean = false,
)