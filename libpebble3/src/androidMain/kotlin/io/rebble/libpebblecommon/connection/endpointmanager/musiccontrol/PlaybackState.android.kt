package io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol

import io.rebble.libpebblecommon.music.PlaybackState

internal fun android.media.session.PlaybackState.toLibPebbleState(): PlaybackState {
    return when (state) {
        android.media.session.PlaybackState.STATE_PLAYING -> PlaybackState.Playing
        android.media.session.PlaybackState.STATE_PAUSED -> PlaybackState.Paused
        android.media.session.PlaybackState.STATE_BUFFERING -> PlaybackState.Buffering
        android.media.session.PlaybackState.STATE_ERROR -> PlaybackState.Error
        else -> PlaybackState.Paused
    }
}