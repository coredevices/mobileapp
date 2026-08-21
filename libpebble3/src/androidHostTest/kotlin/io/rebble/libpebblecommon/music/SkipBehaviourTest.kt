package io.rebble.libpebblecommon.music

import android.media.session.PlaybackState.ACTION_FAST_FORWARD
import android.media.session.PlaybackState.ACTION_REWIND
import android.media.session.PlaybackState.ACTION_SEEK_TO
import android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT
import android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
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
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, enabled))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = false, enabled))
    }

    @Test
    fun `spotify podcast seeks`() {
        assertEquals(SkipBehaviour.SeekTo, skipBehaviour(ACTION_SEEK_TO, forward = true, enabled))
        assertEquals(SkipBehaviour.SeekTo, skipBehaviour(ACTION_SEEK_TO, forward = false, enabled))
    }

    @Test
    fun `pocket casts uses the player's own interval`() {
        val actions = ACTION_FAST_FORWARD or ACTION_REWIND or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.PlayerSeek, skipBehaviour(actions, forward = true, enabled))
        assertEquals(SkipBehaviour.PlayerSeek, skipBehaviour(actions, forward = false, enabled))
    }

    /** YouTube drops previous at the start of a queue; both buttons still skip. */
    @Test
    fun `one skip action is enough to skip both ways`() {
        val actions = ACTION_SKIP_TO_NEXT or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, enabled))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = false, enabled))
    }

    @Test
    fun `no actions skips`() {
        assertEquals(SkipBehaviour.Skip, skipBehaviour(0L, forward = true, enabled))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(0L, forward = false, enabled))
    }

    @Test
    fun `preference off always skips`() {
        val actions = ACTION_FAST_FORWARD or ACTION_REWIND or ACTION_SEEK_TO
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = true, disabled))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(actions, forward = false, disabled))
        assertEquals(SkipBehaviour.Skip, skipBehaviour(ACTION_SEEK_TO, forward = true, disabled))
    }
}
