package io.rebble.libpebblecommon.packets

import assertUByteArrayEquals
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlin.test.Test
import kotlin.test.assertEquals


class MusicTest {
    @Test
    fun `serialize UpdateCurrentTrack with optional parameters`() {
        val packet = MusicControl.UpdateCurrentTrack(
            "A",
            "B",
            "C",
            10,
            20,
            30
        )

        val expectedData = ubyteArrayOf(
            0u, 19u,
            0u, 32u,
            16u,
            1u, 65u,
            1u, 66u,
            1u, 67u,
            10u, 0u, 0u, 0u,
            20u, 0u, 0u, 0u,
            30u, 0u, 0u, 0u
        )

        val actualData = packet.serialize()

        assertUByteArrayEquals(expectedData, actualData)
    }

    @Test
    fun `serialize UpdateCurrentTrack without optional parameters`() {
        val packet = MusicControl.UpdateCurrentTrack(
            "A",
            "B",
            "C"
        )

        val expectedData = ubyteArrayOf(
            0u, 7u,
            0u, 32u,
            16u,
            1u, 65u,
            1u, 66u,
            1u, 67u
        )

        val actualData = packet.serialize()

        assertUByteArrayEquals(expectedData, actualData)
    }

    @Test
    fun `deserialize UpdateCurrentTrack with optional parameters`() {
        val data = ubyteArrayOf(
            0u, 19u,
            0u, 32u,
            16u,
            1u, 65u,
            1u, 66u,
            1u, 67u,
            10u, 0u, 0u, 0u,
            20u, 0u, 0u, 0u,
            30u, 0u, 0u, 0u
        )

        val packet = PebblePacket.deserialize(data) as MusicControl.UpdateCurrentTrack

        assertEquals("A", packet.artist.get())
        assertEquals("B", packet.album.get())
        assertEquals("C", packet.title.get())
        assertEquals(10u, packet.trackLength.get())
        assertEquals(20u, packet.trackCount.get())
        assertEquals(30u, packet.currentTrack.get())
    }

    @Test
    fun `deserialize UpdateCurrentTrack without optional parameters`() {
        val data = ubyteArrayOf(
            0u, 7u,
            0u, 32u,
            16u,
            1u, 65u,
            1u, 66u,
            1u, 67u
        )

        val packet = PebblePacket.deserialize(data) as MusicControl.UpdateCurrentTrack

        assertEquals("A", packet.artist.get())
        assertEquals("B", packet.album.get())
        assertEquals("C", packet.title.get())
        assertEquals(null, packet.trackLength.get())
        assertEquals(null, packet.trackCount.get())
        assertEquals(null, packet.currentTrack.get())
    }

}