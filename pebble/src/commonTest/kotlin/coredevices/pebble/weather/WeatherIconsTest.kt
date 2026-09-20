package coredevices.pebble.weather

import io.rebble.libpebblecommon.imaging.EncodedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherIconsTest {

    private fun EncodedImage.alphaAt(x: Int, y: Int): Int {
        val byte = pixels[y * ((width + 1) / 2) + (x shr 1)].toInt()
        val index = if (x and 1 == 0) (byte shr 4) and 0xF else byte and 0xF
        return (palette[index].toInt() shr 6) and 0x3
    }

    @Test
    fun `an image is a disc on a transparent ground`() {
        val icon = WeatherIcons.image(WeatherPlugin.ICON_SUN, 24)
        assertEquals(24, icon.width)
        assertEquals(24, icon.height)
        assertEquals(0, icon.alphaAt(0, 0), "the corner is outside the disc")
        assertEquals(0x3, icon.alphaAt(12, 12), "the middle of the disc is opaque")
        assertEquals(0x3, icon.alphaAt(2, 12), "the disc reaches the edge at its widest")
    }

    @Test
    fun `an icon is the glyph alone, in one colour`() {
        val icon = WeatherIcons.icon(WeatherPlugin.ICON_SUN, 24)
        assertEquals(0, icon.alphaAt(0, 0), "the corner is empty")
        assertEquals(0x3, icon.alphaAt(12, 12), "the sun is opaque")
        assertTrue(icon.palette.size <= 2, "one ink and a transparent ground")
    }

    @Test
    fun `every condition renders, including one nobody has a glyph for`() {
        val names = listOf(
            WeatherPlugin.ICON_SUN, WeatherPlugin.ICON_PARTLY_CLOUDY, WeatherPlugin.ICON_CLOUDY,
            WeatherPlugin.ICON_LIGHT_RAIN, WeatherPlugin.ICON_HEAVY_RAIN,
            WeatherPlugin.ICON_LIGHT_SNOW, WeatherPlugin.ICON_HEAVY_SNOW,
            WeatherPlugin.ICON_RAIN_AND_SNOW, WeatherPlugin.ICON_UNKNOWN, "no_such_condition",
        )
        names.forEach { name ->
            listOf(WeatherIcons.image(name, 32), WeatherIcons.icon(name, 32)).forEach { icon ->
                assertEquals(32 * 32 / 2, icon.pixels.size, name)
                assertTrue(icon.palette.size in 2..16, name)
            }
        }
    }

    @Test
    fun `the same glyph at the same size is only drawn once`() {
        assertTrue(
            WeatherIcons.image(WeatherPlugin.ICON_CLOUDY, 28) ===
                WeatherIcons.image(WeatherPlugin.ICON_CLOUDY, 28)
        )
    }
}
