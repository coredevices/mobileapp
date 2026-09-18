package io.rebble.libpebblecommon.packets

import assertUByteArrayEquals
import io.rebble.libpebblecommon.music.MusicOutputRoute
import io.rebble.libpebblecommon.music.MusicOutputRoutes
import io.rebble.libpebblecommon.music.MusicOutputRouteStatus
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

    @Test
    fun `serialize and deserialize output route selection`() {
        val packet = MusicControl.SelectOutputRoute(7u, 2u)
        val expectedData = ubyteArrayOf(
            0u, 3u,
            0u, 32u,
            10u, 7u, 2u,
        )

        assertUByteArrayEquals(expectedData, packet.serialize())

        val deserialized = PebblePacket.deserialize(expectedData) as MusicControl.SelectOutputRoute
        assertEquals(7u, deserialized.generation.get())
        assertEquals(2u, deserialized.routeId.get())
    }

    @Test
    fun `serialize output routes`() {
        val packet = MusicControl.UpdateOutputRoutes(
            MusicOutputRoutes(
                status = MusicOutputRouteStatus.Available,
                generation = 5u,
                routes = listOf(
                    MusicOutputRoute(0u, "Phone", true),
                    MusicOutputRoute(1u, "Speaker", false),
                ),
            )
        )
        val expectedData = ubyteArrayOf(
            0u, 22u,
            0u, 32u,
            20u, 0u, 5u, 2u,
            0u, 1u, 5u, 80u, 104u, 111u, 110u, 101u,
            1u, 0u, 7u, 83u, 112u, 101u, 97u, 107u, 101u, 114u,
        )

        assertUByteArrayEquals(expectedData, packet.serialize())
    }
}