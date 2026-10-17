package io.rebble.libpebblecommon.music

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.music.playPauseUsesMediaKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayPauseMediaKeyTest {
    @Test
    fun `legado read-aloud uses media keys`() {
        assertTrue(playPauseUsesMediaKey("io.legado.app.release"))
    }

    @Test
    fun `other players use transport controls`() {
        assertFalse(playPauseUsesMediaKey("com.spotify.music"))
        assertFalse(playPauseUsesMediaKey(null))
    }
}
