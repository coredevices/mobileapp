package io.rebble.libpebblecommon.image

/** [pixels] are non-premultiplied ARGB_8888, row-major. */
public class PebbleBitmap(
    public val width: Int,
    public val height: Int,
    public val pixels: IntArray,
)
