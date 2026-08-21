package coredevices.pebble.weather

import coredevices.util.WeatherUnit
import io.rebble.libpebblecommon.weather.WeatherType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeatherFetcherFieldsTest {

    @Test
    fun parsesUtcOffsetFromColonlessIso8601() {
        assertEquals(-420, "2026-07-20T06:03:00-0700".utcOffsetMinutes())
        assertEquals(540, "2026-07-20T06:03:00+0900".utcOffsetMinutes())
        assertEquals(0, "2026-07-20T06:03:00+0000".utcOffsetMinutes())
        assertEquals(330, "2026-07-20T06:03:00+0530".utcOffsetMinutes())
    }

    @Test
    fun returnsNullForUnparseableOffset() {
        assertNull("2026-07-20T06:03:00Z".utcOffsetMinutes())
        assertNull("".utcOffsetMinutes())
    }

    @Test
    fun mapsHourlyForecastFields() {
        val hour = HourlyForecast(iconCode = 32, temp = 21, uvIndex = 6.44).toHourlyForecast()
        assertEquals(WeatherType.Sun, hour.weatherType)
        assertEquals(21, hour.temp)
        assertEquals(64, hour.uvIndexX10)

        val missing = HourlyForecast(iconCode = 32).toHourlyForecast()
        assertEquals(0, missing.temp)
        assertNull(missing.uvIndexX10)

        // int8 field: out-of-range temps clamp rather than wrap.
        assertEquals(127, HourlyForecast(iconCode = 32, temp = 200).toHourlyForecast().temp)
    }

    @Test
    fun convertsWindToMphOnlyForMetric() {
        assertEquals(5, 8.toMph(WeatherUnit.Metric))
        assertEquals(8, 8.toMph(WeatherUnit.Imperial))
        assertEquals(8, 8.toMph(WeatherUnit.UkHybrid))
        assertNull(null.toMph(WeatherUnit.Metric))
    }
}
