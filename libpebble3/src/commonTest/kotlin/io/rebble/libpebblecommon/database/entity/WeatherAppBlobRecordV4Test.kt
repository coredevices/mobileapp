package io.rebble.libpebblecommon.database.entity

import assertUByteArrayEquals
import io.rebble.libpebblecommon.weather.WeatherDailyForecast
import io.rebble.libpebblecommon.weather.WeatherType
import kotlin.test.Test
import kotlin.test.assertEquals

internal class WeatherAppBlobRecordV4Test {

    /**
     * Golden reference for the firmware's "San Francisco, 18° and Sunny" worked example, extended
     * to minor version 3 (weather_db.h). The fixed block must be exactly 177 bytes — the watch
     * rejects a shorter minor >= 1 record and silently keeps showing the previous one.
     */
    @Test
    fun encodesFullSampleToGoldenBytes() {
        val record = WeatherAppBlobRecordV4(
            currentTemp = 18,
            currentWeatherType = 7u, // Sun
            todayHighTemp = 21,
            todayLowTemp = 13,
            tomorrowWeatherType = 0u, // PartlyCloudy
            tomorrowHighTemp = 18,
            tomorrowLowTemp = 11,
            lastUpdateTimeUtc = 1750000000u,
            isCurrentLocation = true,
            todayFeelsLikeTemp = 17,
            todayUvIndexX10 = 50,
            todayPrecipProbability = 20,
            todayWindSpeed = 12u,
            todayWindDirection = 270u,
            latitudeE2 = 3777,    // 37.77 N
            longitudeE2 = -12242, // 122.42 W
            numDaily = 7u,
            daily = listOf(
                // Day 0 carries the per-day metrics too; they must agree with the today_* fields.
                WeatherDailyForecast(
                    21, 13, WeatherType.Sun,
                    precipProbability = 20, windSpeedMph = 12, uvIndexX10 = 50,
                    feelsLikeTemp = 17, windDirectionDeg = 270,
                    // Day 0 also supplies the minor 2 today_* warning readings.
                    wmoCode = 45, humidityPct = 72, visibilityM = 12200, precipSumMm = 3,
                ),
                WeatherDailyForecast(18, 11, WeatherType.PartlyCloudy),
                WeatherDailyForecast(23, 11, WeatherType.CloudyDay),
                WeatherDailyForecast(13, 5, WeatherType.LightRain),
                WeatherDailyForecast(21, 10, WeatherType.HeavyRain),
                WeatherDailyForecast(16, 7, WeatherType.PartlyCloudy),
                WeatherDailyForecast(20, 12, WeatherType.Sun),
            ),
            todayHourlyCount = 24u,
            todayHourlyWeatherType = UByteArray(24) { 7u },
            todayHourlyTemp = byteArrayOf(
                13, 13, 13, 13, 13, 13, 13, 14, 15, 16, 17, 18,
                19, 20, 20, 21, 20, 20, 19, 17, 16, 15, 15, 14,
            ),
            locationUtcOffsetMin = -420, // UTC-7
            todayWindDirDeg = 270,
            locationName = "San Francisco",
            forecastShort = "Clear",
        )

        val expected = hexToUByteArray(
            """
            04 12 00 07 15 00 0D 00 00 12 00 0B 00 80 E1 4E
            68 01 03 11 00 32 00 14 00 0C 00 0E 01 C1 0E 2E
            D0 07 15 00 0D 00 07 12 00 0B 00 00 17 00 0B 00
            01 0D 00 05 00 03 15 00 0A 00 04 10 00 07 00 00
            14 00 0C 00 07 18 07 07 07 07 07 07 07 07 07 07
            07 07 07 07 07 07 07 07 07 07 07 07 07 07 0D 0D
            0D 0D 0D 0D 0D 0E 0F 10 11 12 13 14 14 15 14 14
            13 11 10 0F 0F 0E 5C FE 14 0C 32 FF FF FF FF FF
            FF FF FF FF FF FF FF FF FF FF FF FF FF 2D 48 A8
            2F 03 00 11 00 FF 7F FF 7F FF 7F FF 7F FF 7F FF
            7F 0E 01 0E 01 FF FF FF FF FF FF FF FF FF FF FF
            FF 16 00 0D 00 53 61 6E 20 46 72 61 6E 63 69 73
            63 6F 05 00 43 6C 65 61 72
            """
        )

        assertEquals(201, expected.size)
        assertUByteArrayEquals(expected, record.toBytes())
    }

    /**
     * The "available-now" phone path leaves UV / precip / wind / hourly unfetched and pads the
     * daily array. Verify those serialize to the firmware's documented sentinels.
     */
    @Test
    fun encodesUnknownSentinels() {
        val record = WeatherAppBlobRecordV4(
            currentTemp = 18,
            currentWeatherType = 7u,
            todayHighTemp = 21,
            todayLowTemp = 13,
            tomorrowWeatherType = 0u,
            tomorrowHighTemp = 18,
            tomorrowLowTemp = 11,
            lastUpdateTimeUtc = 1750000000u,
            isCurrentLocation = false,
            todayFeelsLikeTemp = WEATHER_V4_TEMP_UNKNOWN,
            todayUvIndexX10 = WEATHER_V4_UV_UNKNOWN,
            todayPrecipProbability = WEATHER_V4_PRECIP_UNKNOWN,
            todayWindSpeed = WEATHER_V4_WIND_SPEED_UNKNOWN,
            todayWindDirection = WEATHER_V4_WIND_DIR_UNKNOWN,
            latitudeE2 = WEATHER_V4_COORD_UNKNOWN,
            longitudeE2 = WEATHER_V4_COORD_UNKNOWN,
            numDaily = 1u,
            daily = listOf(WeatherDailyForecast(21, 13, WeatherType.Sun)),
            todayHourlyCount = 0u,
            todayHourlyWeatherType = UByteArray(24) { WeatherType.Unknown.code.toUByte() },
            todayHourlyTemp = ByteArray(24),
            locationUtcOffsetMin = WEATHER_V4_UTC_OFFSET_UNKNOWN,
            todayWindDirDeg = WEATHER_V4_WIND_DIR_DEG_UNKNOWN,
            locationName = "X",
            forecastShort = "Y",
        )

        val bytes = record.toBytes()

        // Fixed portion is 177 bytes; then 2-byte string header + "X" (3) + "Y" (3) = 185 total.
        assertEquals(185, bytes.size)
        // minor_version @18, feels-like @19 (unknown 32767 = FF 7F)
        assertEquals(3u.toUByte(), bytes[18])
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0x7Fu), bytes.sliceArray(19..20)) // feels-like
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(21..22)) // uv = -1
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(23..24)) // precip = -1
        assertUByteArrayEquals(ubyteArrayOf(0x00u, 0x00u), bytes.sliceArray(25..26)) // wind speed = 0
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(27..28)) // wind dir = 0xFFFF
        assertUByteArrayEquals(ubyteArrayOf(0x00u, 0x80u), bytes.sliceArray(29..30)) // lat = -32768
        assertUByteArrayEquals(ubyteArrayOf(0x00u, 0x80u), bytes.sliceArray(31..32)) // lon = -32768
        assertEquals(1u.toUByte(), bytes[33]) // num_daily
        // daily[1] (offset 34 + 5 = 39) is the first padded entry: high/low 32767, type 255.
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0x7Fu, 0xFFu, 0x7Fu, 0xFFu), bytes.sliceArray(39..43))
        assertEquals(0u.toUByte(), bytes[69]) // today_hourly_count
        assertUByteArrayEquals(ubyteArrayOf(0x00u, 0x80u), bytes.sliceArray(118..119)) // utc offset = -32768
        // daily_metrics[7] @120..140, all unknown (255)
        assertUByteArrayEquals(UByteArray(21) { 0xFFu }, bytes.sliceArray(120..140))
        assertEquals(0xFFu.toUByte(), bytes[141]) // wmo code
        assertEquals(0xFFu.toUByte(), bytes[142]) // humidity
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(143..144)) // visibility
        assertUByteArrayEquals(ubyteArrayOf(0xFFu, 0xFFu), bytes.sliceArray(145..146)) // precip sum
        // daily_feels_like[7] @147..160, all 32767; today_wind_dir @161; daily_wind_dir[7] @163..176, all -1
        assertUByteArrayEquals(UByteArray(14) { if (it % 2 == 0) 0xFFu else 0x7Fu }, bytes.sliceArray(147..160))
        assertUByteArrayEquals(UByteArray(16) { 0xFFu }, bytes.sliceArray(161..176))
    }

    private fun hexToUByteArray(hex: String): UByteArray =
        hex.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toUByte(16) }.toUByteArray()
}
