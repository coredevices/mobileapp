package io.rebble.libpebblecommon.imaging

/**
 * Serves the images the phone holds for timeline notifications.
 *
 * Platforms that can't extract images from OS notifications use [NoNotificationImages], which
 * registers nothing — the watch is then told the image type is unsupported and stops asking for it.
 */
interface NotificationImageProvider {
    /** Register with [imagingService] for the connection it belongs to. */
    fun register(imagingService: ImagingService)
}

class NoNotificationImages : NotificationImageProvider {
    override fun register(imagingService: ImagingService) {}
}

// Bounds on the aspect the watch will reserve a band for; the same range is clamped in firmware.
const val MIN_ASPECT_SIXTEENTHS = 4
const val MAX_ASPECT_SIXTEENTHS = 24

/**
 * Height/width of a [width] x [height] image in sixteenths, as sent in
 * `TimelineAttribute.ImageAspectRatio`. Clamped, so very wide or very tall images get letterboxed by
 * the centre-crop at encode time rather than reserving an absurd band on the watch.
 */
fun aspectSixteenths(width: Int, height: Int): UByte? {
    if (width <= 0 || height <= 0) return null
    val sixteenths = (height * 16 + width / 2) / width
    return sixteenths.coerceIn(MIN_ASPECT_SIXTEENTHS, MAX_ASPECT_SIXTEENTHS).toUByte()
}
