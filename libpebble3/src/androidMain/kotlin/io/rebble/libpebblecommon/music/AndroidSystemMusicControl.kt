package io.rebble.libpebblecommon.io.rebble.libpebblecommon.music

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.toLibPebbleState
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.imaging.encodeForWatch
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.NotificationHandler
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.PlayerInfo
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.music.SystemMusicControl
import io.rebble.libpebblecommon.music.isActive
import io.rebble.libpebblecommon.music.matchesTruncated
import io.rebble.libpebblecommon.notification.LibPebbleNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private data class PlaybackStatusWithControls(
    val playbackStatus: PlaybackStatus,
    val transportControls: MediaController.TransportControls,
    val controller: MediaController,
)

private const val SEEK_INTERVAL_MS = 15_000L

internal enum class SkipBehaviour {
    /** Change track. */
    Skip,

    /** The player's own fast forward/rewind, which uses the interval configured in that app. */
    PlayerSeek,

    /** Seek by [SEEK_INTERVAL_MS] ourselves. */
    SeekTo,
}

/**
 * What next/previous should do for a player advertising [actions]: change track where the player
 * supports it, and seek within the current track where it doesn't. Spotify offers no skip on a
 * podcast, which is also where seeking is the more useful thing for those buttons to do.
 *
 * Both skip actions are read together so that a button can't change meaning mid-session: YouTube
 * drops previous at the start of a queue but still means next/previous throughout.
 */
internal fun skipBehaviour(actions: Long, forward: Boolean, watchConfig: WatchConfig): SkipBehaviour {
    val skipActions = PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    val playerSeekAction = if (forward) {
        PlaybackState.ACTION_FAST_FORWARD
    } else {
        PlaybackState.ACTION_REWIND
    }
    return when {
        !watchConfig.musicSeekWhenAvailable -> SkipBehaviour.Skip
        actions and skipActions != 0L -> SkipBehaviour.Skip
        actions and playerSeekAction != 0L -> SkipBehaviour.PlayerSeek
        actions and PlaybackState.ACTION_SEEK_TO != 0L -> SkipBehaviour.SeekTo
        else -> SkipBehaviour.Skip
    }
}

private fun PlaybackState?.seeksWithinTrack(watchConfig: WatchConfig): Boolean =
    skipBehaviour(this?.actions ?: 0L, forward = true, watchConfig) != SkipBehaviour.Skip

/** [PlaybackState.getPosition] is only accurate as of [PlaybackState.getLastPositionUpdateTime]. */
private fun PlaybackState.currentPosition(): Long = if (state == PlaybackState.STATE_PLAYING) {
    position + ((SystemClock.elapsedRealtime() - lastPositionUpdateTime) * playbackSpeed).toLong()
} else {
    position
}

private fun createTrack(metadata: MediaMetadata): MusicTrack {
    return MusicTrack(
        title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
        album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
        length = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).milliseconds,
        trackNumber = metadata.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER).toInt()
            .takeIf {
                it > 0
            },
        totalTracks = metadata.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS).toInt()
            .takeIf {
                it > 0
            }
    )
}

class AndroidSystemMusicControl(
    appContext: AppContext,
    libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val clock: Clock,
    private val notificationAppRealDao: NotificationAppRealDao,
    private val notificationHandler: NotificationHandler,
    private val watchConfigFlow: WatchConfigFlow,
) : SystemMusicControl {
    private val logger = Logger.withTag("AndroidSystemMusicControl")
    private val context = appContext.context
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val notificationServiceComponent = LibPebbleNotificationListener.componentName(context)
    private val packageMostRecentlyStartedPlayingAt: MutableMap<String, Instant> = mutableMapOf()
    private val appNameForPackage: MutableMap<String, String> = mutableMapOf()
    private val _albumArtUpdated = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val albumArtUpdated: Flow<Unit> = _albumArtUpdated

    private fun addCallbackSafely(listener: MediaSessionManager.OnActiveSessionsChangedListener): Boolean {
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                listener,
                notificationServiceComponent
            )
            return true
        } catch (e: SecurityException) {
            return false
        } catch (e: IllegalArgumentException) {
            // Seen inside app-virtualization frameworks (e.g. VLite) that proxy ISessionManager and trip "packageName is not owned by the calling process".
            logger.w(e) { "Media session listener rejected (virtualized/cloned env?)" }
            return false
        }
    }

    private val activeSessions: Flow<List<MediaController>> = callbackFlow {
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            logger.v { "sessions changed: $sessions" }
            trySend(sessions?.mapNotNull { it } ?: emptyList())
        }
        if (!addCallbackSafely(listener)) {
            logger.i { "Couldn't add media listener; waiting for notification access" }
            notificationHandler.notificationServiceBound.first()
            if (!addCallbackSafely(listener)) {
                logger.e { "Couldn't add media listener after notification access granted" }
            }
        }
        try {
            trySend(
                mediaSessionManager.getActiveSessions(notificationServiceComponent)
            )
        } catch (e: SecurityException) {
            logger.e(e) { "Error getting music sessions" }
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "Error getting music sessions (virtualized/cloned env?)" }
        }
        awaitClose {
            mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
        }
    }.flowOn(Dispatchers.Main).onEach {
        logger.d { "Active media sessions changed: ${it.size}" }
    }

    private suspend fun getNameForPackage(packageName: String): String {
        return appNameForPackage[packageName]
            ?: notificationAppRealDao.getEntry(packageName)?.name?.also {
                appNameForPackage[packageName] = it
            } ?: "Unknown"
    }

    private val allSessionsStateFlow: StateFlow<List<PlaybackStatusWithControls>> =
        activeSessions.flatMapLatest { sessions ->
            if (sessions.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            val sessionFlows = sessions.map { session ->
                callbackFlow {
                    val initialPlaybackState = session.playbackState?.toLibPebbleState()
                        ?: io.rebble.libpebblecommon.music.PlaybackState.Paused
                    if (session.playbackState?.position == null) {
                        logger.v { "position null on session callback init" }
                    }
                    var currentState = PlaybackStatusWithControls(
                        playbackStatus = PlaybackStatus(
                            playbackState = initialPlaybackState,
                            currentTrack = session.metadata?.let { createTrack(it) },
                            playbackPositionMs = session.playbackState?.position ?: 0L,
                            playbackRate = session.playbackState?.playbackSpeed ?: 0f,
                            shuffle = false, // TODO: is this used / needed?
                            repeat = RepeatType.Off, // same as above
                            playerInfo = PlayerInfo(
                                packageId = session.packageName,
                                name = getNameForPackage(session.packageName),
                            ),
                            volume = 100, // TODO
                        ),
                        transportControls = session.transportControls,
                        controller = session,
                    )
                    trySend(currentState)

                    val callback = object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            // Media sessions often add the artwork bitmap in a metadata callback
                            // after the initial title/artist one; nudge consumers to retry art.
                            if (metadata?.hasAlbumArt() == true) {
                                _albumArtUpdated.tryEmit(Unit)
                            }
                            val newTrack = metadata?.let { createTrack(it) }
                            val oldTrack = currentState.playbackStatus.currentTrack
                            if (newTrack != oldTrack) {
                                val justAddedArtistOrAlbum = (newTrack?.title == oldTrack?.title) &&
                                        ((!newTrack?.artist.isNullOrEmpty() && oldTrack?.artist.isNullOrEmpty()) ||
                                                (!newTrack?.album.isNullOrEmpty() && oldTrack?.album.isNullOrEmpty()))
                                val newPosition = if (justAddedArtistOrAlbum) {
                                    logger.v { "onMetadataChanged (not resetting position))" }
                                    currentState.playbackStatus.playbackPositionMs
                                } else {
                                    logger.v { "onMetadataChanged (resetting position): new $newTrack != old $oldTrack" }
                                    0
                                }
                                currentState = currentState.copy(
                                    playbackStatus = currentState.playbackStatus.copy(
                                        currentTrack = newTrack,
                                        playbackPositionMs = newPosition,
                                    )
                                )
                            } else {
                                logger.v { "onMetadataChanged (ignored - didn't actually change)" }
                            }
                            trySend(currentState)
                        }

                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            val newPlaybackState = state?.toLibPebbleState()
                                ?: io.rebble.libpebblecommon.music.PlaybackState.Paused
                            if (newPlaybackState == io.rebble.libpebblecommon.music.PlaybackState.Playing
                                && currentState.playbackStatus.playbackState != io.rebble.libpebblecommon.music.PlaybackState.Playing
                            ) {
                                packageMostRecentlyStartedPlayingAt[session.packageName] =
                                    clock.now()
                            }
                            if (state?.position == null) {
                                logger.v { "position null on onPlaybackStateChanged" }
                            }
                            currentState = currentState.copy(
                                playbackStatus = currentState.playbackStatus.copy(
                                    playbackState = newPlaybackState,
                                    playbackPositionMs = state?.position?.takeIf { it > 0 } ?: 0L,
                                    playbackRate = state?.playbackSpeed?.takeIf { it > 0 } ?: 0f,
                                ),
                            )
                            trySend(currentState)
                        }

                        override fun onSessionDestroyed() {
                            close()
                        }
                    }
                    session.registerCallback(callback)
                    awaitClose { session.unregisterCallback(callback) }
                }.flowOn(Dispatchers.Main)
            }
            combine(sessionFlows) { it.toList() }
        }.stateIn(libPebbleCoroutineScope, SharingStarted.Eagerly, emptyList())

    private val targetSession = allSessionsStateFlow
        .runningFold<List<PlaybackStatusWithControls>, PlaybackStatusWithControls?>(null) { previousTarget, newSessions ->
            // Try to find an actively playing session
            val playingSession = newSessions.filter { session ->
                session.playbackStatus.playbackState == io.rebble.libpebblecommon.music.PlaybackState.Playing
            }.maxByOrNull {
                packageMostRecentlyStartedPlayingAt[it.playbackStatus.playerInfo?.packageId]
                    ?: Instant.DISTANT_PAST
            } ?: newSessions.firstOrNull { session ->
                session.playbackStatus.playbackState == io.rebble.libpebblecommon.music.PlaybackState.Buffering
            }

            // Otherwise, if there was a previous target,
            // try to find it in the new list (it might have paused).
            playingSession ?: previousTarget?.let { previous ->
                previous.playbackStatus.playerInfo?.packageId?.let { previousPkg ->
                    newSessions.find {
                        it.playbackStatus.playerInfo?.packageId == previousPkg
                    }
                }
            }
        }.stateIn(libPebbleCoroutineScope, SharingStarted.Eagerly, null)

    // Recomputed here rather than in the session callbacks so that toggling the preference updates
    // the watch's icons without waiting for the player to report a state change.
    override val playbackState: StateFlow<PlaybackStatus?> =
        combine(targetSession, watchConfigFlow.flow) { session, config ->
            val state = session?.controller?.playbackState
            session?.playbackStatus?.copy(
                skipSeeksWithinTrack = state.seeksWithinTrack(config.watchConfig),
            )
        }.stateIn(libPebbleCoroutineScope, SharingStarted.Eagerly, null)

    override fun play() {
        logger.d { "Playing media" }
        targetSession.value?.transportControls?.play() ?: run {
            // Fallback to audio manager if no session is available
            logger.w { "No active media session found, falling back to AudioManager for play" }
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
        }
    }

    override fun pause() {
        logger.d { "Pausing playback" }
        targetSession.value?.transportControls?.pause()
    }

    override fun playPause() {
        targetSession.value?.playbackStatus?.playbackState?.let {
            when {
                it.isActive() -> pause()
                else -> play() // Fallback to play if not playing or paused
            }
        } ?: run {
            logger.i { "No playback state available, defaulting to play" }
            play()
        }
    }

    override fun nextTrack() = skipOrSeek(forward = true)

    override fun previousTrack() = skipOrSeek(forward = false)

    private fun skipOrSeek(forward: Boolean) {
        val session = targetSession.value ?: return
        val controls = session.transportControls
        val state = session.controller.playbackState
        when (skipBehaviour(state?.actions ?: 0L, forward, watchConfigFlow.value)) {
            SkipBehaviour.Skip ->
                if (forward) controls.skipToNext() else controls.skipToPrevious()

            SkipBehaviour.PlayerSeek ->
                if (forward) controls.fastForward() else controls.rewind()

            SkipBehaviour.SeekTo -> {
                val offset = if (forward) SEEK_INTERVAL_MS else -SEEK_INTERVAL_MS
                controls.seekTo(((state?.currentPosition() ?: 0L) + offset).coerceAtLeast(0))
            }
        }
    }

    override fun volumeDown() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    override fun volumeUp() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    override val supportsAlbumArt: Boolean = true

    override suspend fun getAlbumArt(title: String, artist: String, width: Int, height: Int): EncodedImage? =
        withContext(Dispatchers.Default) {
            // Read the metadata once: the track it names and the bitmap it holds must be the same
            // snapshot, or a track change mid-request sends art for the wrong song.
            val metadata = targetSession.value?.controller?.metadata ?: return@withContext null
            if (!matchesTruncated(metadata.getString(MediaMetadata.METADATA_KEY_TITLE), title) ||
                !matchesTruncated(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST), artist)
            ) {
                logger.d { "Album art request no longer matches the current track" }
                return@withContext null
            }
            val bitmap = metadata.albumArtBitmap()
            if (bitmap == null) {
                logger.d { "No album art bitmap on current metadata" }
                return@withContext null
            }
            logger.d { "Encoding album art ${bitmap.width}x${bitmap.height} -> ${width}x${height}" }
            bitmap.encodeForWatch(width, height)
        }
}

private fun MediaMetadata.albumArtBitmap() =
    getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

// containsKey, not getBitmap: getBitmap decodes the whole bitmap, and this runs on every metadata change.
private fun MediaMetadata.hasAlbumArt() = containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART)
        || containsKey(MediaMetadata.METADATA_KEY_ART)
        || containsKey(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
