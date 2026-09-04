package io.rebble.libpebblecommon.music

import android.media.session.PlaybackState.ACTION_FAST_FORWARD
import android.media.session.PlaybackState.ACTION_PAUSE
import android.media.session.PlaybackState.ACTION_PLAY_PAUSE
import android.media.session.PlaybackState.ACTION_REWIND
import android.media.session.PlaybackState.ACTION_SEEK_TO
import android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT
import android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
import android.media.session.PlaybackState.ACTION_STOP
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.SkipBehaviour
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.skipBehaviour
import kotlin.test.Test
import kotlin.test.assertEquals

class SkipBehaviourTest {
    private val enabled = WatchConfig(musicSeekWhenAvailable = true)
    private val disabled = WatchConfig(musicSeekWhenAvailable = false)

    @Test
    fun `spotify music skips`() {
        val actions = ACTION_SKIP_TO_NEXT or ACTION_SKIP_TO_PREVIOUS or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, enabled, SPOTIFY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = false, enabled, SPOTIFY))
    }

    @Test
    fun `spotify podcast seeks`() {
        assertEquals(SkipBehaviour.SeekTo, skipBehaviour(ACTION_SEEK_TO, forward = true, enabled, SPOTIFY))
        assertEquals(SkipBehaviour.SeekTo, skipBehaviour(ACTION_SEEK_TO, forward = false, enabled, SPOTIFY))
    }

    @Test
    fun `pocket casts uses the player's own interval`() {
        val actions = ACTION_FAST_FORWARD or ACTION_REWIND or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.PlayerSeek, skipBehaviour(actions, forward = true, enabled, SPOTIFY))
        assertEquals(SkipBehaviour.PlayerSeek, skipBehaviour(actions, forward = false, enabled, SPOTIFY))
    }

    /** YouTube drops previous at the start of a queue; both buttons still skip. */
    @Test
    fun `one skip action is enough to skip both ways`() {
        val actions = ACTION_SKIP_TO_NEXT or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, enabled, SPOTIFY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = false, enabled, SPOTIFY))
    }

    @Test
    fun `no actions skips`() {
        assertEquals(SkipBehaviour.Skip, skipBehaviour(0L, forward = true, enabled, SPOTIFY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(0L, forward = false, enabled, SPOTIFY))
    }

    @Test
    fun `audiobook players seek despite advertising skip`() {
        val actions = ACTION_SKIP_TO_NEXT or ACTION_SKIP_TO_PREVIOUS or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.SeekTo, skipBehaviour(actions, forward = true, enabled, AUDIBLE))
        assertEquals(SkipBehaviour.SeekTo, skipBehaviour(actions, forward = false, enabled, AUDIBLE))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, enabled, SPOTIFY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, disabled, AUDIBLE))
    }

    /** Libby's real action set: no skip, no seek, no fast forward. */
    @Test
    fun `libby uses a media button`() {
        val actions = ACTION_STOP or ACTION_PAUSE or ACTION_PLAY_PAUSE
        assertEquals(SkipBehaviour.MediaKey, skipBehaviour(actions, forward = true, enabled, LIBBY))
        assertEquals(SkipBehaviour.MediaKey, skipBehaviour(actions, forward = false, enabled, LIBBY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, disabled, LIBBY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, enabled, AUDIBLE))
    }

    @Test
    fun `preference off always skips`() {
        val actions = ACTION_FAST_FORWARD or ACTION_REWIND or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, disabled, SPOTIFY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = false, disabled, SPOTIFY))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(ACTION_SEEK_TO, forward = true, disabled, SPOTIFY))
    }

    private companion object {
        const val SPOTIFY = "com.spotify.music"
        const val LIBBY = "com.overdrive.mobile.android.libby"
        const val AUDIBLE = "com.audible.application"
    }
}
