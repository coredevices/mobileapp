package coredevices.pebble.weather

import coredevices.util.WeatherUnit
import io.rebble.libpebblecommon.plugin.LongTextShape
import io.rebble.libpebblecommon.plugin.NumericSeries
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.SeriesShape
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.TextSeries
import io.rebble.libpebblecommon.plugin.TimestampSeries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// 2026-10-27 in UTC+1: local midnight is 23:00Z on the 26th.
private val NOW = Instant.parse("2026-10-27T10:30:00Z")
private val MIDNIGHT = Instant.parse("2026-10-26T23:00:00Z").epochSeconds
private const val OFFSET_MIN = 60

private inline fun <reified T> shape(name: String, value: T): Map<String, JsonElement> = mapOf(name to Json.encodeToJsonElement(value))

private fun numeric(value: Double, unit: String? = null) = shape(SourceShapeNames.NUMERIC_VALUE, NumericValueShape(value, unit))

private fun series(start: Long, step: Long, series: SeriesShape) = shape(SourceShapeNames.SERIES, series.copy(start = start, stepSec = step))

private fun instance(
    daily: Int = 3,
    withSun: Boolean = true,
    hourlyFrom: Long = MIDNIGHT + 10 * 3600,
    dailyFrom: Long = MIDNIGHT,
) = SourceInstance(
    "00000000-0000-0000-0003-000000000001",
    buildMap {
        put(WeatherPlugin.PROPERTY_TEMPERATURE, numeric(12.4, "°C"))
        put(WeatherPlugin.PROPERTY_FEELS_LIKE, numeric(10.0, "°C"))
        put(WeatherPlugin.PROPERTY_CONDITION_CODE, shape(SourceShapeNames.LONG_TEXT, LongTextShape("light_rain")))
        put(
            WeatherPlugin.PROPERTY_CONDITION,
            shape(SourceShapeNames.SHORT_TEXT, ShortTextShape("Light rain")) +
                shape(SourceShapeNames.LONG_TEXT, LongTextShape("Light rain showers and thunder")),
        )
        put(WeatherPlugin.PROPERTY_WIND_SPEED, numeric(5.0, "m/s"))
        put(WeatherPlugin.PROPERTY_WIND_DIRECTION, numeric(270.0))
        put(WeatherPlugin.PROPERTY_HUMIDITY, numeric(81.0, "%"))
        put(WeatherPlugin.PROPERTY_UTC_OFFSET, numeric(OFFSET_MIN.toDouble()))
        val days = (0 until daily)
        put(WeatherPlugin.PROPERTY_DAILY_HIGH, series(dailyFrom, 86400, SeriesShape(numericValue = NumericSeries(days.map { 14.0 + it }, "°C"))))
        put(WeatherPlugin.PROPERTY_DAILY_LOW, series(dailyFrom, 86400, SeriesShape(numericValue = NumericSeries(days.map { 6.0 + it }, "°C"))))
        put(WeatherPlugin.PROPERTY_DAILY_CONDITION_CODE, series(dailyFrom, 86400, SeriesShape(longText = TextSeries(days.map { "sun" }))))
        put(WeatherPlugin.PROPERTY_DAILY_PRECIPITATION, series(dailyFrom, 86400, SeriesShape(numericValue = NumericSeries(days.map { 40.0 }, "%"))))
        if (withSun) {
            put(WeatherPlugin.PROPERTY_DAILY_SUNRISE, series(dailyFrom, 86400, SeriesShape(timestamp = TimestampSeries(days.map { dailyFrom + it * 86400 + 7 * 3600 }))))
            put(WeatherPlugin.PROPERTY_DAILY_SUNSET, series(dailyFrom, 86400, SeriesShape(timestamp = TimestampSeries(days.map { dailyFrom + it * 86400 + 17 * 3600 }))))
        }
        // From 10:00 local: the hours before it aren't known.
        put(
            WeatherPlugin.PROPERTY_HOURLY_TEMPERATURE,
            series(hourlyFrom, 3600, SeriesShape(numericValue = NumericSeries((0 until 30).map { 10.0 + it % 5 }, "°C"))),
        )
        put(
            WeatherPlugin.PROPERTY_HOURLY_CONDITION_CODE,
            series(hourlyFrom, 3600, SeriesShape(longText = TextSeries((0 until 30).map { "sun" }))),
        )
    },
)

class PluginWeatherTest {
    private fun response(units: WeatherUnit = WeatherUnit.Metric, instance: SourceInstance = instance()) =
        pluginWeatherResponse(instance, units, NOW, phoneUtcOffsetMin = 0)

    @Test
    fun currentConditionsInTheUsersUnits() {
        val metric = response()!!.conditions.data.observation
        assertEquals(12, metric.metric!!.temp)
        assertEquals(14, metric.metric!!.max24Hour)
        assertEquals(18, metric.windSpeed)
        assertEquals(270, metric.windDirection)
        assertEquals(11, metric.iconCode)
        assertEquals("Light rain", metric.phrase12Char.trim())

        val imperial = response(WeatherUnit.Imperial)!!.conditions.data.observation
        assertNull(imperial.metric)
        assertEquals(54, imperial.imperial!!.temp)
        assertEquals(11, imperial.windSpeed)
    }

    @Test
    fun daysCarryLocalSunriseAndSunset() {
        val days = response()!!.fcstdaily7.data.forecasts
        assertEquals(3, days.size)
        assertEquals("2026-10-27T07:00:00+0100", days[0].sunriseRaw)
        assertEquals(Instant.parse("2026-10-28T16:00:00Z"), days[1].sunset)
        assertEquals(60, days[0].sunriseRaw.utcOffsetMinutes())
        assertEquals(15, days[1].maxTemp)
        assertEquals(40, days[0].pop)
        assertEquals(81, days[0].humidityPct)
        assertEquals(32, days[0].day!!.iconCode)
        assertEquals(31, days[0].night.iconCode)
    }

    @Test
    fun hoursRunFromLocalMidnightTakingTheNearestKnownHour() {
        val hours = response()!!.fcsthourly!!.data.hours
        assertEquals(48, hours.size)
        assertEquals(10, hours[0].temp)
        assertEquals(10, hours[10].temp)
        assertEquals(11, hours[11].temp)
        // Before 07:00 and from 17:00 it's night: the moon, not the sun.
        assertEquals(31, hours[0].iconCode)
        assertEquals(32, hours[10].iconCode)
        assertEquals(31, hours[17].iconCode)
    }

    @Test
    fun dailySeriesAreReadFromTodayWhereverTheyStart() {
        val fromYesterday = response(instance = instance(daily = 4, dailyFrom = MIDNIGHT - 86400))!!.fcstdaily7.data.forecasts
        assertEquals(3, fromYesterday.size)
        assertEquals(15, fromYesterday[0].maxTemp)
        assertEquals("2026-10-27T07:00:00+0100", fromYesterday[0].sunriseRaw)

        assertNull(response(instance = instance(dailyFrom = MIDNIGHT + 86400)))
    }

    @Test
    fun withoutTwoFullDaysOrSunTimesThereIsNothingToFeedTheWatch() {
        assertNull(response(instance = instance(daily = 1)))
        assertNull(response(instance = instance(withSun = false)))
    }

    @Test
    fun iso8601MatchesTheBackendsFormat() {
        assertEquals("2026-10-27T05:12:09-0730", iso8601(Instant.parse("2026-10-27T12:42:09Z"), -450))
        assertEquals(Instant.parse("2026-10-27T12:42:09Z"), Instant.parse("2026-10-27T05:12:09-07:30"))
    }
}
