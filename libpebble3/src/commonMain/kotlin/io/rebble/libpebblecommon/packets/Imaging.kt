package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.util.Endian
import kotlin.uuid.Uuid

/**
 * Generic image-fetch endpoint (0x35). The watch pulls an image from the phone: it sends a
 * [Request] naming the type, encoding and dimensions, and the phone replies with chunked [Response]
 * messages.
 */
open class Imaging(val message: Message) : PebblePacket(ProtocolEndpoint.IMAGING) {
    val command = SUByte(m, message.value)

    init {
        type = command.get()
    }

    enum class Message(val value: UByte) {
        Request(0x01u),
        Response(0x02u),
    }

    enum class ImageType(val value: UByte) {
        AlbumArt(0x00u),
        NotificationImage(0x01u),
        ;

        companion object {
            fun from(value: UByte): ImageType? = entries.firstOrNull { it.value == value }
        }
    }

    enum class Format(val value: UByte) {
        BlackWhite1Bit(0x00u),
        Color8Bit(0x01u),
        Palette4Bit(0x02u),
    }

    /**
     * Watch -> phone request head, common to every image type: [token], [imageType], [format] and
     * the target [width]/[height]. Each [ImageType] has its own subclass adding its type-specific
     * parameters after the head; the decoder ([imagingPacketsRegister]) picks the subclass by the
     * [imageType] byte on the wire.
     */
    open class Request(
        imageType: ImageType = ImageType.AlbumArt,
        token: UByte = 0u,
        format: UByte = 0u,
        width: UShort = 0u,
        height: UShort = 0u,
    ) : Imaging(Message.Request) {
        val token = SUByte(m, token)
        val imageType = SUByte(m, imageType.value)
        val format = SUByte(m, format)
        val width = SUShort(m, width, endianness = Endian.Little)
        val height = SUShort(m, height, endianness = Endian.Little)
    }

    /**
     * Album-art request: the head plus the current track's [title] and [artist], so the phone can
     * return art matching the track the watch is showing (and could look it up by name, e.g. an iOS
     * web service).
     */
    class AlbumArtRequest(
        token: UByte = 0u,
        format: UByte = 0u,
        width: UShort = 0u,
        height: UShort = 0u,
        title: String = "",
        artist: String = "",
    ) : Request(ImageType.AlbumArt, token, format, width, height) {
        val title = SString(m, title)
        val artist = SString(m, artist)
    }

    /**
     * Image for a timeline notification: the head plus the notification's item id, which is the key
     * the phone cached the image under.
     */
    class NotificationImageRequest(
        token: UByte = 0u,
        format: UByte = 0u,
        width: UShort = 0u,
        height: UShort = 0u,
        itemId: Uuid = Uuid.NIL,
    ) : Request(ImageType.NotificationImage, token, format, width, height) {
        val itemId = SUUID(m, itemId)
    }

    /**
     * Phone -> watch, chunked. The [body] is pre-assembled (chunk flags/offset/len, the image
     * header on the first chunk, and the pixel data) and serialised as raw trailing bytes.
     */
    class Response(body: UByteArray = ubyteArrayOf()) : Imaging(Message.Response) {
        val body = SBytes(m, body.size, body)
    }
}

fun imagingPacketsRegister() {
    // All requests share the Request command byte; the image-type byte (after the 4-byte frame
    // header, command and token) selects the concrete request class to decode into.
    val imageTypeOffset = 4 + 1 + 1
    PacketRegistry.register(ProtocolEndpoint.IMAGING, Imaging.Message.Request.value) { bytes ->
        when (bytes.getOrNull(imageTypeOffset)) {
            Imaging.ImageType.AlbumArt.value -> Imaging.AlbumArtRequest()
            Imaging.ImageType.NotificationImage.value -> Imaging.NotificationImageRequest()
            else -> Imaging.Request()
        }
    }
}
