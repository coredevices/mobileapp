package coredevices.pebble.weather

import coredevices.database.WeatherLocationEntity
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.weather.WeatherHourlyForecast
import io.rebble.libpebblecommon.weather.WeatherType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private val LONDON = Uuid.parse("00000000-0000-0000-0003-000000000001")
private val PARIS = Uuid.parse("00000000-0000-0000-0003-000000000002")
private val ORPHAN = Uuid.parse("00000000-0000-0000-0003-000000000003")

private fun entry(key: Uuid, name: String) = WeatherAppEntry(
    key = key,
    currentTemp = 10,
    currentWeatherType = 0,
    todayHighTemp = 12,
    todayLowTemp = 5,
    tomorrowWeatherType = 0,
    tomorrowHighTemp = 13,
    tomorrowLowTemp = 6,
    lastUpdateTimeUtcSecs = 0,
    isCurrentLocation = false,
    locationName = name,
    forecastShort = "",
)

private fun location(key: Uuid, name: String, orderIndex: Int) = WeatherLocationEntity(
    key = key,
    orderIndex = orderIndex,
    name = name,
    latitude = null,
    longitude = null,
    currentLocation = false,
)

class WeatherPluginTest {

    @Test
    fun entriesFollowTheUsersLocationOrder() {
        val ordered = listOf(entry(LONDON, "London"), entry(PARIS, "Paris"))
            .inLocationOrder(listOf(location(PARIS, "Paris", 0), location(LONDON, "London", 1)))

        assertEquals(listOf("Paris", "London"), ordered.map { it.locationName })
    }

    @Test
    fun entriesWithNoMatchingLocationGoLast() {
        val ordered = listOf(entry(ORPHAN, "Orphan"), entry(LONDON, "London"))
            .inLocationOrder(listOf(location(LONDON, "London", 0)))

        assertEquals(listOf("London", "Orphan"), ordered.map { it.locationName })
    }
}

class ConditionCodeTest {

    // The vocabulary published in new-plugin-api.md: a watchface ships one picture per code, so
    // a code that isn't on this list would arrive with nothing to draw for it.
    private val published = setOf(
        WeatherPlugin.ICON_SUN, WeatherPlugin.ICON_PARTLY_CLOUDY, WeatherPlugin.ICON_CLOUDY,
        WeatherPlugin.ICON_LIGHT_RAIN, WeatherPlugin.ICON_HEAVY_RAIN,
        WeatherPlugin.ICON_LIGHT_SNOW, WeatherPlugin.ICON_HEAVY_SNOW,
        WeatherPlugin.ICON_RAIN_AND_SNOW, WeatherPlugin.ICON_UNKNOWN,
    )

    @Test
    fun everyConditionTheWatchReportsHasAPublishedCode() {
        val codes = WeatherType.entries.associateWith { conditionCodeOf(it.code) }
        assertEquals(emptyMap(), codes.filterValues { it !in published })
        assertEquals(WeatherPlugin.ICON_HEAVY_RAIN, codes[WeatherType.HeavyRain])
        assertEquals(WeatherPlugin.ICON_LIGHT_SNOW, codes[WeatherType.LightSnow])
        assertEquals(WeatherPlugin.ICON_PARTLY_CLOUDY, codes[WeatherType.PartlyCloudy])
        // Generic is the firmware's "some weather" — no picture to name, same as Unknown.
        assertEquals(WeatherPlugin.ICON_UNKNOWN, codes[WeatherType.Generic])
    }

    @Test
    fun aConditionThisBuildHasNeverHeardOfIsUnknown() {
        assertEquals(WeatherPlugin.ICON_UNKNOWN, conditionCodeOf(Byte.MAX_VALUE))
    }
}

class HoursAheadTest {

    private fun hours(from: Int, count: Int) = List(count) { index ->
        WeatherHourlyForecast(weatherType = WeatherType.Sun, temp = (from + index).toByte())
    }

    private val entry = entry(LONDON, "London").copy(
        // The series is indexed by the location's local hour: [0] is midnight.
        todayHourly = hours(0, 24),
        tomorrowHourly = hours(100, 24),
    )

    @Test
    fun skipsTheHoursAlreadyGone() {
        val ahead = entry.hoursAhead(localHour = 14).take(3).map { it.temp.toInt() }
        assertEquals(listOf(15, 16, 17), ahead)
    }

    @Test
    fun rollsIntoTomorrowAtTheEndOfTheDay() {
        val ahead = entry.hoursAhead(localHour = 22).take(3).map { it.temp.toInt() }
        assertEquals(listOf(23, 100, 101), ahead)
    }

    @Test
    fun aForecastWithNoHoursOffersNone() {
        assertEquals(emptyList(), entry(PARIS, "Paris").hoursAhead(localHour = 9))
        assertEquals(emptyList(), null.hoursAhead(localHour = 9))
    }
}
