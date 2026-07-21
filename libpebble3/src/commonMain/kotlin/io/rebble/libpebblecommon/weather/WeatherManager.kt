package io.rebble.libpebblecommon.weather

import io.rebble.libpebblecommon.connection.Weather
import io.rebble.libpebblecommon.database.dao.WeatherAppRealDao
import io.rebble.libpebblecommon.database.entity.AppPrefsEntryDao
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.database.entity.WeatherPrefsValue
import io.rebble.libpebblecommon.database.entity.setWeatherSettings
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

class WeatherManager(
    private val weatherAppEntryDao: WeatherAppRealDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val appPrefsEntryDao: AppPrefsEntryDao,
): Weather {
    override fun updateWeatherData(weatherData: List<WeatherLocationData>) {
        libPebbleCoroutineScope.launch {
            val existing = weatherAppEntryDao.getAll()
            val toDelete = existing.filter { existingEntry ->
                weatherData.none { it.key == existingEntry.key }
            }
            toDelete.forEach {
                weatherAppEntryDao.markForDeletion(it.key)
            }
            weatherAppEntryDao.insertOrReplace(weatherData.mapNotNull {
                (it as? WeatherLocationData.WeatherLocationDataPopulated)?.toWeatherAppEntry()
            })
            appPrefsEntryDao.setWeatherSettings(WeatherPrefsValue(weatherData.map { it.key }))
        }
    }
}

fun WeatherLocationData.WeatherLocationDataPopulated.toWeatherAppEntry() = WeatherAppEntry(
    key = key,
    currentTemp = currentTemp,
    currentWeatherType = currentWeatherType.code,
    todayHighTemp = todayHighTemp,
    todayLowTemp = todayLowTemp,
    tomorrowWeatherType = tomorrowWeatherType.code,
    tomorrowHighTemp = tomorrowHighTemp,
    tomorrowLowTemp = tomorrowLowTemp,
    lastUpdateTimeUtcSecs = lastUpdateTimeUtcSecs,
    isCurrentLocation = isCurrentLocation,
    locationName = locationName,
    forecastShort = forecastShort,
    todayFeelsLikeTemp = todayFeelsLikeTemp,
    latitude = latitude,
    longitude = longitude,
    dailyForecast = dailyForecast,
    todayUvIndexX10 = todayUvIndexX10,
    todayPrecipProbability = todayPrecipProbability,
    todayWindSpeed = todayWindSpeed,
    todayWindDirection = todayWindDirection,
    todayHourly = todayHourly,
    locationUtcOffsetMin = locationUtcOffsetMin,
)

@Serializable
enum class WeatherType(val code: Byte) {
    PartlyCloudy(0),
    CloudyDay(1),
    LightSnow(2),
    LightRain(3),
    HeavyRain(4),
    HeavySnow(5),
    Generic(6),
    Sun(7),
    RainAndSnow(8),
    Unknown(255u.toByte()),
}

/**
 * One day of the v4 daily forecast carried to the watch. The extras land in the v4 minor 1-3
 * per-day blocks; null is encoded as the firmware's "unknown" sentinel. Wind speed is mph
 * regardless of the user's unit — the watch prints "mph" literally.
 */
@Serializable
data class WeatherDailyForecast(
    val highTemp: Short,
    val lowTemp: Short,
    val weatherType: WeatherType,
    val precipProbability: Short? = null,
    val windSpeedMph: Int? = null,
    val uvIndexX10: Short? = null,
    val feelsLikeTemp: Short? = null,
    val windDirectionDeg: Int? = null,
    // The record only carries these for today, but they're per-day readings, so they live
    // here and the encoder projects index 0 into the minor 2 today_* fields.
    val wmoCode: Int? = null,
    val humidityPct: Int? = null,
    val visibilityM: Int? = null,
    val precipSumMm: Int? = null,
)

/** One hour of today's v4 forecast (diurnal curve) carried to the watch. */
@Serializable
data class WeatherHourlyForecast(
    val weatherType: WeatherType,
    val temp: Byte,
)

sealed class WeatherLocationData {
    abstract val key: Uuid
    data class WeatherLocationDataFailed(
        override val key: Uuid,
    ) : WeatherLocationData()
    data class WeatherLocationDataPopulated(
        override val key: Uuid,
        val currentTemp: Short,
        val currentWeatherType: WeatherType,
        val todayHighTemp: Short,
        val todayLowTemp: Short,
        val tomorrowWeatherType: WeatherType,
        val tomorrowHighTemp: Short,
        val tomorrowLowTemp: Short,
        val lastUpdateTimeUtcSecs: Long,
        val isCurrentLocation: Boolean,
        val locationName: String,
        val forecastShort: String,
        // v4-only extras. Only sent when the watch advertises SupportsWeatherDbV4;
        // null/empty is encoded as the firmware's "unknown" sentinels.
        val todayFeelsLikeTemp: Short? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val dailyForecast: List<WeatherDailyForecast> = emptyList(),
        val todayUvIndexX10: Short? = null,
        val todayPrecipProbability: Short? = null,
        val todayWindSpeed: Int? = null,
        val todayWindDirection: Int? = null,
        val todayHourly: List<WeatherHourlyForecast> = emptyList(),
        /** The city's own UTC offset in minutes including DST, so the watch can show its local times. */
        val locationUtcOffsetMin: Int? = null,
    ) : WeatherLocationData()
}