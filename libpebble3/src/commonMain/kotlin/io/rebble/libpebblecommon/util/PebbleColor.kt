package io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.timeline.TimelineColor
import kotlinx.serialization.Serializable

/**
 * Represents an ARGB8888 color, which is converted to an ARGB2222 color for the Pebble
 */
@Serializable
data class PebbleColor(
    val alpha: UByte,
    val red: UByte,
    val green: UByte,
    val blue: UByte
)

fun PebbleColor.toProtocolNumber() =
    (((alpha / 85u) shl 6) or
    ((red / 85u) shl 4) or
    ((green / 85u) shl 2) or
    (blue / 85u)).toUByte()

/**
 * Converts a 32-bit ARGB8888 integer color to a PebbleColor
 */
fun Int.toPebbleColor(): PebbleColor {
    val a = (this shr 24) and 0xFF
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF

    return PebbleColor(
        a.toUByte(),
        r.toUByte(),
        g.toUByte(),
        b.toUByte()
    )
}

fun UByte.toPebbleColor(): PebbleColor {
    val a = (this.toUInt() shr 6) and 3u
    val r = (this.toUInt() shr 4) and 3u
    val g = (this.toUInt() shr 2) and 3u
    val b = this.toUInt() and 3u

    return PebbleColor(
        (a * 85u).toUByte(),
        (r * 85u).toUByte(),
        (g * 85u).toUByte(),
        (b * 85u).toUByte()
    )
}

fun PebbleColor.asTimelineColor(): TimelineColor {
    val r = (red / 85u) * 85u
    val g = (green / 85u) * 85u
    val b = (blue / 85u) * 85u
    val rgb = ((r.toUInt() shl 16) or (g.toUInt() shl 8) or b.toUInt()).toInt()
    return TimelineColor.entries.first { it.color == rgb }
}
