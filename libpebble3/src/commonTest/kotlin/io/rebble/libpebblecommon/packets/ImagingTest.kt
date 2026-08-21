package io.rebble.libpebblecommon.packets

import assertIs
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

internal class ImagingTest {
    @Test
    fun albumArtRequestRoundTrips() {
        val original = Imaging.AlbumArtRequest(
            token = 0x2Au,
            format = Imaging.Format.Palette4Bit.value,
            width = 166u,
            height = 166u,
            title = "Mrs. Robinson",
            artist = "Simon & Garfunkel",
        )

        val decoded = PebblePacket.deserialize(original.serialize())

        // The image-type byte selects the concrete request subclass.
        assertIs<Imaging.AlbumArtRequest>(decoded)
        assertEquals(0x2Au.toUByte(), decoded.token.get())
        assertEquals(Imaging.ImageType.AlbumArt.value, decoded.imageType.get())
        assertEquals(Imaging.Format.Palette4Bit.value, decoded.format.get())
        assertEquals(166, decoded.width.get().toInt())
        assertEquals(166, decoded.height.get().toInt())
        assertEquals("Mrs. Robinson", decoded.title.get())
        assertEquals("Simon & Garfunkel", decoded.artist.get())
    }

    @Test
    fun notificationImageRequestRoundTrips() {
        val itemId = Uuid.parse("18584a55-29a4-4f2f-9143-b5973dd7a423")
        val original = Imaging.NotificationImageRequest(
            token = 0x09u,
            format = Imaging.Format.Palette4Bit.value,
            width = 180u,
            height = 135u,
            itemId = itemId,
        )

        val decoded = PebblePacket.deserialize(original.serialize())

        assertIs<Imaging.NotificationImageRequest>(decoded)
        assertEquals(0x09u.toUByte(), decoded.token.get())
        assertEquals(Imaging.ImageType.NotificationImage.value, decoded.imageType.get())
        assertEquals(180, decoded.width.get().toInt())
        assertEquals(135, decoded.height.get().toInt())
        assertEquals(itemId, decoded.itemId.get())
    }

    @Test
    fun unknownImageTypeDecodesToBaseRequest() {
        val original = Imaging.Request().apply {
            token.set(1u)
            imageType.set(0x7Fu) // not a known ImageType
            width.set(64u)
            height.set(64u)
        }

        val decoded = PebblePacket.deserialize(original.serialize())

        assertIs<Imaging.Request>(decoded)
        assertFalse(decoded is Imaging.AlbumArtRequest)
        assertEquals(0x7Fu.toUByte(), decoded.imageType.get())
    }
}
