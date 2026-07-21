package io.rebble.libpebblecommon.database.entity

import assertUByteArrayEquals
import io.rebble.libpebblecommon.weather.WeatherDailyForecast
import io.rebble.libpebblecommon.weather.WeatherHourlyForecast
import io.rebble.libpebblecommon.weather.WeatherType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Covers the [WeatherAppEntry] -> [WeatherAppBlobRecordV4] mapping (the v4-only fields and their
 * "unknown" sentinel fallbacks). The byte layout itself is pinned by [WeatherAppBlobRecordV4Test];
 * here we only assert the fields this mapping is responsible for, at their fixed offsets.
 *
 * The fixed prefix + daily[7] are constant-width, so v4 today-extras land at stable offsets:
 * uv @21, precip @23, wind speed @25, wind direction @27, hourly count @69,
 * hourly weather types @70..93, hourly temps @94..117.
 */
internal class WeatherAppEntryV4MappingTest {

    private fun entry(
        todayUvIndexX10: Short? = null,
        todayPrecipProbability: Short? = null,
        todayWindSpeed: Int? = null,
        todayWindDirection: Int? = null,
        todayHourly: List<WeatherHourlyForecast>? = null,
        locationUtcOffsetMin: Int? = null,
        dailyForecast: List<WeatherDailyForecast> = listOf(WeatherDailyForecast(21, 13, WeatherType.Sun)),
    ) = WeatherAppEntry(
        key = Uuid.parse("00000000-0000-0000-0000-000000000000"),
        currentTemp = 18,
        currentWeatherType = WeatherType.Sun.code,
        todayHighTemp = 21,
        todayLowTemp = 13,
        tomorrowWeatherType = WeatherType.PartlyCloudy.code,
        tomorrowHighTemp = 18,
        tomorrowLowTemp = 11,
        lastUpdateTimeUtcSecs = 1750000000,
        isCurrentLocation = true,
        locationName = "X",
        forecastShort = "Y",
        dailyForecast = dailyForecast,
        todayUvIndexX10 = todayUvIndexX10,
        todayPrecipProbability = todayPrecipProbability,
        todayWindSpeed = todayWindSpeed,
        todayWindDirection = todayWindDirection,
        todayHourly = todayHourly,
        locationUtcOffsetMin = locationUtcOffsetMin,
    )

    private fun le16(value: Int) = ubyteArrayOf((value and 0xFF).toUByte(), ((value shr 8) and 0xFF).toUByte())

    @Test
    fun mapsPopulatedV4Fields() {
        val hourly = listOf(
            WeatherHourlyForecast(WeatherType.Sun, 20),
            WeatherHourlyForecast(WeatherType.CloudyDay, -5),
            WeatherHourlyForecast(WeatherType.LightRain, 12),
        )
        val bytes = entry(
            todayUvIndexX10 = 62,
            todayPrecipProbability = 40,
            todayWindSpeed = 15,
            todayWindDirection = 200,
            todayHourly = hourly,
        ).v4Record().toBytes()

        assertUByteArrayEquals(le16(62), bytes.sliceArray(21..22))  // uv x10
        assertUByteArrayEquals(le16(40), bytes.sliceArray(23..24))  // precip %
        assertUByteArrayEquals(le16(15), bytes.sliceArray(25..26))  // wind speed
        assertUByteArrayEquals(le16(200), bytes.sliceArray(27..28)) // wind direction

        assertEquals(3u.toUByte(), bytes[69])                       // hourly count
        // First three hourly weather types, then padding = Unknown (255).
        assertEquals(WeatherType.Sun.code.toUByte(), bytes[70])
        assertEquals(WeatherType.CloudyDay.code.toUByte(), bytes[71])
        assertEquals(WeatherType.LightRain.code.toUByte(), bytes[72])
        assertEquals(WeatherType.Unknown.code.toUByte(), bytes[73])
        // First three hourly temps (int8, -5 == 0xFB), then padding = 0.
        assertEquals(20u.toUByte(), bytes[94])
        assertEquals(0xFBu.toUByte(), bytes[95])
        assertEquals(12u.toUByte(), bytes[96])
        assertEquals(0u.toUByte(), bytes[97])
    }

    @Test
    fun fallsBackToSentinelsWhenV4FieldsMissing() {
        val bytes = entry().v4Record().toBytes()

        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(21..22)) // uv = -1
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(23..24)) // precip = -1
        assertUByteArrayEquals(ubyteArrayOf(0x00u, 0x00u), bytes.sliceArray(25..26)) // wind speed = 0
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(27..28)) // wind dir = 0xFFFF
        assertEquals(0u.toUByte(), bytes[69])                                        // hourly count = 0
        assertUByteArrayEquals(ubyteArrayOf(0x00u, 0x80u), bytes.sliceArray(118..119)) // utc offset = -32768
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(161..162)) // wind dir deg = -1
    }

    @Test
    fun mapsMinor1To3PerDayFields() {
        val bytes = entry(
            todayWindDirection = 200,
            locationUtcOffsetMin = 540, // Tokyo
            dailyForecast = listOf(
                WeatherDailyForecast(
                    21, 13, WeatherType.Sun,
                    precipProbability = 40, windSpeedMph = 15, uvIndexX10 = 62,
                    feelsLikeTemp = 19, windDirectionDeg = 200,
                ),
                WeatherDailyForecast(18, 11, WeatherType.PartlyCloudy, precipProbability = 70, uvIndexX10 = 30),
            ),
        ).v4Record().toBytes()

        assertEquals(3u.toUByte(), bytes[18])                                  // minor_version
        assertUByteArrayEquals(le16(540), bytes.sliceArray(118..119))          // utc offset
        // daily_metrics[0] @120, [1] @123 (no wind for day 1), [2] @126 padded unknown
        assertUByteArrayEquals(ubyteArrayOf(40u, 15u, 62u), bytes.sliceArray(120..122))
        assertUByteArrayEquals(ubyteArrayOf(70u, 0xFFu, 30u), bytes.sliceArray(123..125))
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu, 0xFFu), bytes.sliceArray(126..128))
        assertUByteArrayEquals(le16(19), bytes.sliceArray(147..148))           // daily_feels_like[0]
        assertUByteArrayEquals(le16(32767), bytes.sliceArray(149..150))        // daily_feels_like[1] unknown
        assertUByteArrayEquals(le16(200), bytes.sliceArray(161..162))          // today_wind_dir_deg
        assertUByteArrayEquals(le16(200), bytes.sliceArray(163..164))          // daily_wind_dir_deg[0]
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(165..166)) // [1] = -1
    }

    @Test
    fun projectsDayZeroWarningReadingsIntoTodayFields() {
        val bytes = entry(
            dailyForecast = listOf(
                WeatherDailyForecast(
                    21, 13, WeatherType.Sun,
                    wmoCode = 45, humidityPct = 72, visibilityM = 12200, precipSumMm = 3,
                ),
            ),
        ).v4Record().toBytes()

        assertEquals(45u.toUByte(), bytes[141])                        // today_wmo_code
        assertEquals(72u.toUByte(), bytes[142])                        // today_humidity_pct
        assertUByteArrayEquals(le16(12200), bytes.sliceArray(143..144)) // today_visibility_m
        assertUByteArrayEquals(le16(3), bytes.sliceArray(145..146))     // today_precip_sum_mm
    }

    /** 0xFFFF is reserved for "unknown", so real readings clamp to 65534 instead. */
    @Test
    fun clampsVisibilityAndPrecipBelowTheUnknownSentinel() {
        val bytes = entry(
            dailyForecast = listOf(
                WeatherDailyForecast(21, 13, WeatherType.Sun, visibilityM = 99999, precipSumMm = 70000),
            ),
        ).v4Record().toBytes()

        assertUByteArrayEquals(le16(65534), bytes.sliceArray(143..144))
        assertUByteArrayEquals(le16(65534), bytes.sliceArray(145..146))
    }

    /** The watch drops any minor >= 1 record whose fixed block is short of 177 bytes. */
    @Test
    fun fixedBlockIsAlways177Bytes() {
        val bytes = entry().v4Record().toBytes()
        assertEquals(177 + 2 + 3 + 3, bytes.size) // fixed + string header + "X" + "Y"
    }

    @Test
    fun clampsHourlyToTwentyFourEntries() {
        val bytes = entry(
            todayHourly = List(30) { WeatherHourlyForecast(WeatherType.Sun, 10) },
        ).v4Record().toBytes()

        assertEquals(24u.toUByte(), bytes[69]) // count capped at WEATHER_DB_HOURLY_COUNT
    }
}
