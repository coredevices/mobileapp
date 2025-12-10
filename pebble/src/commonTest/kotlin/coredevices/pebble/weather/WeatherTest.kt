package coredevices.pebble.weather

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WeatherTest {
    private val JSON = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun deserializerUTC() {
        val input = """
{
          "class": "fod_long_range_daily",
          "dow": "Monday",
          "expire_time_gmt": 1764639013,
          "fcst_valid": 1764572400,
          "fcst_valid_local": "2025-12-01T07:00:00+0000",
          "lunar_phase": "Waxing Gibbous",
          "lunar_phase_code": "WXG",
          "lunar_phase_day": 11,
          "max_temp": null,
          "min_temp": 6,
          "moonrise": "2025-12-01T13:37:16+0000",
          "moonset": "2025-12-01T02:53:10+0000",
          "night": {
            "alt_daypart_name": "Tonight",
            "clds": 70,
            "day_ind": "N",
            "daypart_name": "Tonight",
            "fcst_valid": 1764572400,
            "fcst_valid_local": "2025-12-01T07:00:00+0000",
            "golf_category": "boring sports",
            "hi": 8,
            "icon_code": 11,
            "icon_extd": 1100,
            "long_daypart_name": "Tonight",
            "narrative": "Considerable cloudiness with occasional rain showers. Low 6C. Winds SSW at 10 to 15 km/h. Chance of rain 100%.",
            "phrase_12char": "Showers",
            "phrase_22char": "Showers",
            "phrase_32char": "Showers",
            "pop": 98,
            "precip_type": "rain",
            "qpf": 1,
            "qualifier": null,
            "qualifier_code": null,
            "rh": 97,
            "shortcast": "Showers",
            "snow_qpf": 0,
            "snow_range": "",
            "temp": 6,
            "temp_phrase": "Low 6C.",
            "thunder_enum": 0,
            "thunder_enum_phrase": "No thunder",
            "uv_desc": "Low",
            "uv_index": 0,
            "uv_index_raw": 0,
            "wc": 5,
            "wdir": 210,
            "wdir_cardinal": "SSW",
            "wind_phrase": "Winds SSW at 10 to 15 km/h.",
            "wspd": 12
          },
          "qpf": 1,
          "snow_qpf": 0,
          "sunrise": "2025-12-01T07:43:28+0000",
          "sunset": "2025-12-01T15:56:26+0000"
        }
        """.trimIndent()
        val output = JSON.decodeFromString(DailyForecast.serializer(), input)
        assertEquals(Instant.parse("2025-12-01T07:43:28Z"), output.sunrise)
        assertEquals(Instant.parse("2025-12-01T15:56:26Z"), output.sunset)
    }

    @Test
    fun deserializerPT() {
        val input = """
            {
          "class": "fod_long_range_daily",
          "dow": "Monday",
          "expire_time_gmt": 1764639106,
          "fcst_valid": 1764601200,
          "fcst_valid_local": "2025-12-01T07:00:00-0800",
          "lunar_phase": "Waxing Gibbous",
          "lunar_phase_code": "WXG",
          "lunar_phase_day": 11,
          "max_temp": null,
          "min_temp": 7,
          "moonrise": "2025-12-01T14:17:58-0800",
          "moonset": "2025-12-01T03:10:13-0800",
          "night": {
            "alt_daypart_name": "Tonight",
            "clds": 45,
            "day_ind": "N",
            "daypart_name": "Tonight",
            "fcst_valid": 1764601200,
            "fcst_valid_local": "2025-12-01T07:00:00-0800",
            "golf_category": "boring sports",
            "hi": 11,
            "icon_code": 33,
            "icon_extd": 3300,
            "long_daypart_name": "Tonight",
            "narrative": "A few passing clouds, otherwise generally clear. Low 7C. Winds light and variable.",
            "phrase_12char": "M Clear",
            "phrase_22char": "Mostly Clear",
            "phrase_32char": "Mostly Clear",
            "pop": 5,
            "precip_type": "rain",
            "qpf": 0,
            "qualifier": null,
            "qualifier_code": null,
            "rh": 82,
            "shortcast": "Mostly Clear",
            "snow_qpf": 0,
            "snow_range": "",
            "temp": 7,
            "temp_phrase": "Low 7C.",
            "thunder_enum": 0,
            "thunder_enum_phrase": "No thunder",
            "uv_desc": "Low",
            "uv_index": 0,
            "uv_index_raw": 0,
            "wc": 7,
            "wdir": 45,
            "wdir_cardinal": "NE",
            "wind_phrase": "Winds light and variable.",
            "wspd": 9
          },
          "qpf": 0,
          "snow_qpf": 0,
          "sunrise": "2025-12-01T07:06:37-0800",
          "sunset": "2025-12-01T16:50:45-0800"
        }
        """.trimIndent()
        val output = JSON.decodeFromString(DailyForecast.serializer(), input)
        assertEquals(Instant.parse("2025-12-01T15:06:37Z"), output.sunrise)
        assertEquals(Instant.parse("2025-12-02T00:50:45Z"), output.sunset)
    }
}