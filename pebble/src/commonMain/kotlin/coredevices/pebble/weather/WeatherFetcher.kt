package coredevices.pebble.weather

import co.touchlab.kermit.Logger
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.SystemAppIDs.WEATHER_APP_UUID
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.buildTimelinePin
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WeatherFetcher(
    private val systemGeolocation: SystemGeolocation,
    private val coreConfigFlow: CoreConfigFlow,
    private val pebbleWebServices: RealPebbleWebServices,
    private val libPebble: LibPebble,
    private val clock: Clock,
) {
    private val logger = Logger.withTag("WeatherFetcher")

    suspend fun fetchWeather() {
        if (!coreConfigFlow.value.weatherPins) {
            Day.entries.forEach {
                libPebble.delete(it.dayUuid)
                libPebble.delete(it.nightUuid)
            }
            return
        }
        val location = systemGeolocation.getCurrentPosition()
        when (location) {
            is GeolocationPositionResult.Error -> {
                logger.i { "Couldn't get location: ${location.message}" }
                return
            }
            is GeolocationPositionResult.Success -> {
                val response = pebbleWebServices.getWeather(location)
                if (response != null) {
                    createTimelinePins(response)
                }
            }
        }
    }

    private fun createTimelinePins(weather: WeatherResponse) {
        val today = weather.fcstdaily7.data.forecasts.getOrNull(0)
        val tomorrow = weather.fcstdaily7.data.forecasts.getOrNull(1)
        val dayAfterTomorrow = weather.fcstdaily7.data.forecasts.getOrNull(2)
        createTimelinePins(today, Day.Today)
        createTimelinePins(tomorrow, Day.Tomorrow)
        createTimelinePins(dayAfterTomorrow, Day.DayAfterTomorrow)
    }

    private fun createTimelinePins(dailyForecast: DailyForecast?, day: Day) {
        if (dailyForecast == null) {
            libPebble.delete(day.dayUuid)
            libPebble.delete(day.nightUuid)
            return
        }
        if (dailyForecast.day != null) {
            createTimelinePin(
                title = "Sunrise",
                subtitle = "${dailyForecast.day.temp}°/${dailyForecast.night.temp}°",
                dayOrNight = dailyForecast.day,
                timestamp = dailyForecast.sunrise,
                uuid = day.dayUuid,
                location = "Current Location",
            )
        } else {
            libPebble.delete(day.dayUuid)
        }

        createTimelinePin(
            title = "Sunset",
            subtitle = "-/${dailyForecast.night.temp}°",
            dayOrNight = dailyForecast.night,
            timestamp = dailyForecast.sunset,
            uuid = day.nightUuid,
            location = "Current Location",
        )
    }

    private fun createTimelinePin(
        uuid: Uuid,
        title: String,
        subtitle: String,
        dayOrNight: DailyDayNight,
        timestamp: Instant,
        location: String,
    ) {
        val pin = buildTimelinePin(
            parentId = WEATHER_APP_UUID,
            timestamp = timestamp,
        ) {
            itemID = uuid
            duration = Duration.ZERO
            layout = TimelineItem.Layout.WeatherPin
            attributes {
                title { title }
                subtitle { subtitle }
                body { dayOrNight.narrative }
                tinyIcon { dayOrNight.iconCode.toWeatherIcon() }
                largeIcon { dayOrNight.iconCode.toWeatherIcon() }
                lastUpdated { clock.now() }
                location { location }
            }
        }
        libPebble.insertOrReplace(pin)
    }
}

enum class Day(
    val dayUuid: Uuid,
    val nightUuid: Uuid,
) {
    Today(TodayDayUuid, TodayNightUuid),
    Tomorrow(TomorrowDayUuid, TomorrowNightUuid),
    DayAfterTomorrow(DayAfterTomorrowDayUuid, DayAfterTomorrowNightUuid),
}

private val TodayDayUuid = Uuid.parse("b0beeaa7-a6cf-46c6-8bc4-2700ed3fa952")
private val TodayNightUuid = Uuid.parse("ef6abfda-5312-4649-a10a-427235059479")
private val TomorrowDayUuid = Uuid.parse("2834d0c8-85bc-45bd-b2b7-d0f026098d28")
private val TomorrowNightUuid = Uuid.parse("2f363ad4-bc5d-455a-a445-4e00ec07417e")
private val DayAfterTomorrowDayUuid = Uuid.parse("3233984e-2dc9-4469-8a9a-46f91d81b7fc")
private val DayAfterTomorrowNightUuid = Uuid.parse("fd97c081-9970-436b-aa87-9c55232bce35")

object Iso8601InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val string = decoder.decodeString()
        // The API returns a non-standard ISO-8601 date string.
        // The timezone offset is missing a colon.
        // Examples: 2025-12-01T13:37:16+0000 and 2025-12-01T14:17:58-0800
        // We need to insert the colon to make it parseable by Instant.
        // i.e. 2025-12-01T13:37:16+00:00
        val sanitized = string.substring(0, string.length - 2) + ":" + string.substring(string.length - 2)
        return Instant.parse(sanitized)
    }
}

fun Int.toWeatherIcon(): TimelineIcon = when (this) {
    in 0..4 -> TimelineIcon.HeavyRain
    in 5..8 -> TimelineIcon.LightSnow
    9 -> TimelineIcon.LightRain
    10 -> TimelineIcon.LightSnow
    11 -> TimelineIcon.LightRain
    12 -> TimelineIcon.HeavyRain
    in 13..14 -> TimelineIcon.LightSnow
    in 15..17 -> TimelineIcon.HeavySnow
    18 -> TimelineIcon.LightSnow
    in 19..22 -> TimelineIcon.CloudyDay
    in 23..25 -> TimelineIcon.TimelineWeather
    in 26..28 -> TimelineIcon.CloudyDay
    in 29..30 -> TimelineIcon.PartlyCloudy
    in 31..34 -> TimelineIcon.TimelineSun
    35 -> TimelineIcon.LightSnow
    36 -> TimelineIcon.TimelineSun
    in 37..40 -> TimelineIcon.HeavyRain
    in 41..43 -> TimelineIcon.HeavySnow
    44 -> TimelineIcon.TimelineWeather
    45 -> TimelineIcon.LightRain
    46 -> TimelineIcon.LightSnow
    47 -> TimelineIcon.HeavyRain
    else -> TimelineIcon.TimelineWeather
}

@Serializable
data class WeatherResponse(
    val fcstdaily7: DailyForecasts
)

@Serializable
data class DailyForecasts(
    val data: DailyForecastData
)

@Serializable
data class DailyForecastData(
    val forecasts: List<DailyForecast>
)

@Serializable
data class DailyForecast(
    @Serializable(with = Iso8601InstantSerializer::class)
    val sunrise: Instant,
    @Serializable(with = Iso8601InstantSerializer::class)
    val sunset: Instant,
    val day: DailyDayNight? = null,
    val night: DailyDayNight,
)

@Serializable
data class DailyDayNight(
    val narrative: String,
    val temp: Int,
    @SerialName("icon_code")
    val iconCode: Int,
)
