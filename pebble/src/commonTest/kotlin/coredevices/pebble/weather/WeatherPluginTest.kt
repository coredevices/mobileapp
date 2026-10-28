package coredevices.pebble.weather

import coredevices.database.WeatherLocationEntity
import coredevices.database.WeatherLocationDao
import io.rebble.libpebblecommon.connection.FakeLibPebble
import io.rebble.libpebblecommon.connection.Weather
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.SeriesShape
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.TimestampShape
import io.rebble.libpebblecommon.weather.WeatherDailyForecast
import io.rebble.libpebblecommon.weather.WeatherHourlyForecast
import io.rebble.libpebblecommon.weather.WeatherLocationData
import io.rebble.libpebblecommon.weather.WeatherType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
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

class ForecastDayStartTest {

    @Test
    fun startsAtLocalMidnightOnTheDayItWasFetched() {
        // 15:30 UTC is 17:30 at UTC+2, so the day began at 22:00 UTC the evening before.
        val fetched = Instant.parse("2026-10-21T15:30:00Z").epochSeconds
        val entry = entry(PARIS, "Paris").copy(lastUpdateTimeUtcSecs = fetched, locationUtcOffsetMin = 120)
        assertEquals(Instant.parse("2026-10-20T22:00:00Z").epochSeconds, entry.forecastDayStart())
    }

    @Test
    fun aLocationBehindUtcCanStillBeOnTheDayBefore() {
        // 03:00 UTC is 22:00 the previous evening at UTC-5.
        val fetched = Instant.parse("2026-10-21T03:00:00Z").epochSeconds
        val entry = entry(PARIS, "Paris").copy(lastUpdateTimeUtcSecs = fetched, locationUtcOffsetMin = -300)
        assertEquals(Instant.parse("2026-10-20T05:00:00Z").epochSeconds, entry.forecastDayStart())
    }
}

private class FakeWeather(entries: List<WeatherAppEntry>) : Weather {
    override val currentWeather = MutableStateFlow(entries)
    override fun updateWeatherData(weatherData: List<WeatherLocationData>) {}
}

private class FakeWeatherLocationDao(private val locations: List<WeatherLocationEntity>) : WeatherLocationDao {
    override suspend fun upsert(location: WeatherLocationEntity) {}
    override suspend fun delete(location: WeatherLocationEntity) {}
    override suspend fun updateOrder(key: Uuid, newIndex: Int) {}
    override fun getAllLocationsFlow() = flowOf(locations)
    override suspend fun getAllLocations() = locations
}

class WeatherSeriesTest {

    private val fetched = Instant.parse("2026-10-21T09:00:00Z").epochSeconds
    private val midnight = Instant.parse("2026-10-21T00:00:00Z").epochSeconds

    private val london = entry(LONDON, "London").copy(
        lastUpdateTimeUtcSecs = fetched,
        locationUtcOffsetMin = 0,
        todayHourly = List(24) { WeatherHourlyForecast(WeatherType.Sun, temp = it.toByte()) },
        tomorrowHourly = List(24) { WeatherHourlyForecast(WeatherType.LightRain, temp = (100 + it).toByte()) },
        todayWindSpeed = 10,
        todayWindDirection = 270,
        dailyForecast = listOf(
            WeatherDailyForecast(
                highTemp = 18, lowTemp = 9, weatherType = WeatherType.Sun, precipProbability = 10, humidityPct = 70,
                sunriseUtcSecs = midnight + 7 * 3600, sunsetUtcSecs = midnight + 17 * 3600,
            ),
            WeatherDailyForecast(highTemp = TEMP_NO_VALUE, lowTemp = 7, weatherType = WeatherType.HeavyRain),
        ),
    )

    private val plugin = WeatherPlugin(
        FakeWeather(listOf(london)),
        FakeWeatherLocationDao(listOf(location(LONDON, "London", 0))),
        FakeLibPebble(),
    )

    private suspend fun read(properties: List<String>?, iconPixelSize: IconPixelSize? = null) =
        plugin.observe(WeatherPlugin.CATEGORY, WeatherPlugin.ITEM_LOCATION, properties, iconPixelSize)
            .first().instances.single().properties

    private fun Map<String, Map<String, JsonElement>>.series(property: String): SeriesShape =
        Json.decodeFromJsonElement(getValue(property).getValue(SourceShapeNames.SERIES))

    @Test
    fun todaysWindHumidityOffsetAndSunAreServed() = runTest {
        val properties = read(null)
        fun number(property: String) =
            Json.decodeFromJsonElement<NumericValueShape>(properties.getValue(property).getValue(SourceShapeNames.NUMERIC_VALUE)).value

        assertEquals(10.0, number(WeatherPlugin.PROPERTY_WIND_SPEED))
        assertEquals(270.0, number(WeatherPlugin.PROPERTY_WIND_DIRECTION))
        assertEquals(70.0, number(WeatherPlugin.PROPERTY_HUMIDITY))
        assertEquals(0.0, number(WeatherPlugin.PROPERTY_UTC_OFFSET))
        assertEquals(
            midnight + 7 * 3600,
            Json.decodeFromJsonElement<TimestampShape>(properties.getValue(WeatherPlugin.PROPERTY_SUNRISE).getValue(SourceShapeNames.TIMESTAMP)).value,
        )
        assertEquals(listOf(midnight + 17 * 3600, null), properties.series(WeatherPlugin.PROPERTY_DAILY_SUNSET).timestamp!!.values)
    }

    @Test
    fun hoursRunFromLocalMidnightThroughTomorrow() = runTest {
        val hourly = read(null).series(WeatherPlugin.PROPERTY_HOURLY_TEMPERATURE)

        assertEquals(midnight, hourly.start)
        assertEquals(3600L, hourly.stepSec)
        val temps = hourly.numericValue!!.values
        assertEquals(48, temps.size)
        assertEquals(listOf(0.0, 1.0), temps.take(2))
        assertEquals(100.0, temps[24])
    }

    @Test
    fun daysStepByADayAndAMissingHighIsAGap() = runTest {
        val properties = read(null)
        val highs = properties.series(WeatherPlugin.PROPERTY_DAILY_HIGH)

        assertEquals(midnight, highs.start)
        assertEquals(86_400L, highs.stepSec)
        assertEquals(listOf(18.0, null), highs.numericValue!!.values)
        assertEquals(listOf("sun", "heavy_rain"), properties.series(WeatherPlugin.PROPERTY_DAILY_CONDITION_CODE).longText!!.values)
        assertEquals(listOf(10.0, null), properties.series(WeatherPlugin.PROPERTY_DAILY_PRECIPITATION).numericValue!!.values)
    }

    @Test
    fun onlyTheAskedForSeriesAreBuilt() = runTest {
        val properties = read(listOf(WeatherPlugin.PROPERTY_HOURLY_CONDITION), IconPixelSize(26, 26))

        val series = properties.filterValues { SourceShapeNames.SERIES in it }.keys
        assertEquals(setOf(WeatherPlugin.PROPERTY_HOURLY_CONDITION), series)
        val condition = properties.series(WeatherPlugin.PROPERTY_HOURLY_CONDITION)
        assertEquals(48, condition.icon!!.values.size)
        assertEquals(26, condition.icon!!.values.first()!!.width)
    }

    @Test
    fun glyphsAreOnlyDrawnWhenAskedFor() = runTest {
        val condition = read(null).series(WeatherPlugin.PROPERTY_HOURLY_CONDITION)

        assertEquals(null, condition.icon)
        assertEquals("Sunny", condition.shortText!!.values.first())
    }

    @Test
    fun thereIsNoLongerAnHourItem() = runTest {
        assertEquals(false, plugin.serves(WeatherPlugin.CATEGORY, "hour"))
    }
}
