package io.rebble.libpebblecommon.io.rebble.libpebblecommon.music

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.toLibPebbleState
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.NotificationHandler
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.PlayerInfo
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.music.SystemMusicControl
import io.rebble.libpebblecommon.music.isActive
import io.rebble.libpebblecommon.notification.LibPebbleNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

private data class PlaybackStatusWithControls(
    val playbackStatus: PlaybackStatus,
    val transportControls: MediaController.TransportControls,
)

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
) : SystemMusicControl {
    private val logger = Logger.withTag("AndroidSystemMusicControl")
    private val context = appContext.context
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val notificationServiceComponent = LibPebbleNotificationListener.componentName(context)
    private val packageMostRecentlyStartedPlayingAt: MutableMap<String, Instant> = mutableMapOf()
    private val appNameForPackage: MutableMap<String, String> = mutableMapOf()

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
                    )
                    trySend(currentState)

                    val callback = object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) {
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

    override val playbackState: StateFlow<PlaybackStatus?> =
        targetSession.map { it?.playbackStatus }
            .stateIn(libPebbleCoroutineScope, SharingStarted.Eagerly, null)

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

    override fun nextTrack() {
        targetSession.value?.transportControls?.skipToNext()
    }

    override fun previousTrack() {
        targetSession.value?.transportControls?.skipToPrevious()
    }

    override fun volumeDown() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    override fun volumeUp() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }
}