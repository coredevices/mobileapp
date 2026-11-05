package io.rebble.libpebblecommon.io.rebble.libpebblecommon.music

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.imaging.ImageEncoder
import io.rebble.libpebblecommon.music.ITunesAlbumArtLookup
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.SystemMusicControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

class IosSystemMusicControl(
    private val watchConfigFlow: WatchConfigFlow,
    private val albumArtLookup: ITunesAlbumArtLookup,
) : SystemMusicControl {
    private val logger = Logger.withTag("IosSystemMusicControl")

    override fun play() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun playPause() {
        TODO("Not yet implemented")
    }

    override fun nextTrack() {
        TODO("Not yet implemented")
    }

    override fun previousTrack() {
        TODO("Not yet implemented")
    }

    override fun volumeDown() {
        TODO("Not yet implemented")
    }

    override fun volumeUp() {
        TODO("Not yet implemented")
    }

    override val playbackState: StateFlow<PlaybackStatus?> = MutableStateFlow(null).asStateFlow()

    override val supportsAlbumArt: Boolean = true

    override val albumArtByRequestedTrack: Boolean = true

    override suspend fun getAlbumArt(title: String, artist: String, width: Int, height: Int): EncodedImage? {
        if (!watchConfigFlow.value.lookUpAlbumArtOnline) return null
        val bytes = albumArtLookup.artwork(title, artist, maxOf(width, height)) ?: return null
        return withContext(Dispatchers.Default) {
            val argb = decodeAlbumArtArgb(bytes, width, height)
            if (argb == null) {
                logger.w { "couldn't decode album art (${bytes.size} bytes)" }
                return@withContext null
            }
            ImageEncoder.encode(argb, width, height)
        }
    }

    override val albumArtUpdated: Flow<Unit> = emptyFlow()
}
