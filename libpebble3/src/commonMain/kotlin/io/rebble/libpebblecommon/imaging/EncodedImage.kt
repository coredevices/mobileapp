package io.rebble.libpebblecommon.imaging

/**
 * An image encoded for the watch: a 4-bpp palettized bitmap. [palette] holds up to 16 GColor8
 * bytes; [pixels] holds 4-bpp indices packed two-per-byte (even x = high nibble), rows padded to
 * ceil([width] / 2) bytes. Produced by the platform image encoder and chunked onto the wire by
 * [ImagingService].
 */
class EncodedImage(
    val width: Int,
    val height: Int,
    val palette: UByteArray,
    val pixels: UByteArray,
)
