package io.rebble.libpebblecommon.database.entity

import co.touchlab.kermit.Logger
import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SBoolean
import io.rebble.libpebblecommon.structmapper.SFixedList
import io.rebble.libpebblecommon.structmapper.SLongString
import io.rebble.libpebblecommon.structmapper.SShort
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.Endian
import io.rebble.libpebblecommon.weather.WeatherDailyForecast
import io.rebble.libpebblecommon.weather.WeatherHourlyForecast
import io.rebble.libpebblecommon.weather.WeatherType
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@GenerateRoomEntity(
    primaryKey = "key",
    databaseId = BlobDatabase.Weather,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class WeatherAppEntry(
    val key: Uuid,
    val currentTemp: Short,
    val currentWeatherType: Byte,
    val todayHighTemp: Short,
    val todayLowTemp: Short,
    val tomorrowWeatherType: Byte,
    val tomorrowHighTemp: Short,
    val tomorrowLowTemp: Short,
    val lastUpdateTimeUtcSecs: Long,
    val isCurrentLocation: Boolean,
    val locationName: String,
    val forecastShort: String,
    // v4-only extras (nullable so the Room column auto-migrates for existing rows).
    // Only emitted when the watch advertises SupportsWeatherDbV4; missing values
    // are encoded as the firmware's documented "unknown" sentinels.
    val todayFeelsLikeTemp: Short? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dailyForecast: List<WeatherDailyForecast>? = null,
    val todayUvIndexX10: Short? = null,
    val todayPrecipProbability: Short? = null,
    val todayWindSpeed: Int? = null,
    val todayWindDirection: Int? = null,
    val todayHourly: List<WeatherHourlyForecast>? = null,
    val locationUtcOffsetMin: Int? = null,
) : BlobDbItem {
    override fun key(): UByteArray = SUUID(StructMapper(), key).toBytes()

    override fun value(params: ValueParams): UByteArray? {
        if (!params.capabilities.contains(ProtocolCapsFlag.SupportsWeatherApp)) {
            return null
        }
        if (params.capabilities.contains(ProtocolCapsFlag.SupportsWeatherDbV4)) {
            Logger.v { "using weather v4" }
            return v4Record().toBytes()
        }
        return WeatherAppBlobRecord(
            currentTemp = currentTemp,
            currentWeatherType = currentWeatherType.toUByte(),
            todayHighTemp = todayHighTemp,
            todayLowTemp = todayLowTemp,
            tomorrowWeatherType = tomorrowWeatherType.toUByte(),
            tomorrowHighTemp = tomorrowHighTemp,
            tomorrowLowTemp = tomorrowLowTemp,
            lastUpdateTimeUtc = lastUpdateTimeUtcSecs.toUInt(),
            isCurrentLocation = isCurrentLocation,
            locationName = locationName,
            forecastShort = forecastShort,
        ).toBytes()
    }

    internal fun v4Record(): WeatherAppBlobRecordV4 {
        val daily = (dailyForecast ?: emptyList()).take(WEATHER_DB_MAX_FORECAST_DAYS)
        val hourly = (todayHourly ?: emptyList()).take(WEATHER_DB_HOURLY_COUNT)
        return WeatherAppBlobRecordV4(
            currentTemp = currentTemp,
            currentWeatherType = currentWeatherType.toUByte(),
            todayHighTemp = todayHighTemp,
            todayLowTemp = todayLowTemp,
            tomorrowWeatherType = tomorrowWeatherType.toUByte(),
            tomorrowHighTemp = tomorrowHighTemp,
            tomorrowLowTemp = tomorrowLowTemp,
            lastUpdateTimeUtc = lastUpdateTimeUtcSecs.toUInt(),
            isCurrentLocation = isCurrentLocation,
            todayFeelsLikeTemp = todayFeelsLikeTemp ?: WEATHER_V4_TEMP_UNKNOWN,
            todayUvIndexX10 = todayUvIndexX10 ?: WEATHER_V4_UV_UNKNOWN,
            todayPrecipProbability = todayPrecipProbability ?: WEATHER_V4_PRECIP_UNKNOWN,
            todayWindSpeed = todayWindSpeed?.toUShort() ?: WEATHER_V4_WIND_SPEED_UNKNOWN,
            todayWindDirection = todayWindDirection?.toUShort() ?: WEATHER_V4_WIND_DIR_UNKNOWN,
            latitudeE2 = latitude.toCoordE2(),
            longitudeE2 = longitude.toCoordE2(),
            numDaily = daily.size.toUByte(),
            daily = daily,
            todayHourlyCount = hourly.size.toUByte(),
            todayHourlyWeatherType = UByteArray(WEATHER_DB_HOURLY_COUNT) { i ->
                hourly.getOrNull(i)?.weatherType?.code?.toUByte() ?: WeatherType.Unknown.code.toUByte()
            },
            todayHourlyTemp = ByteArray(WEATHER_DB_HOURLY_COUNT) { i -> hourly.getOrNull(i)?.temp ?: 0 },
            locationUtcOffsetMin = locationUtcOffsetMin?.toShort() ?: WEATHER_V4_UTC_OFFSET_UNKNOWN,
            todayWindDirDeg = todayWindDirection?.toShort() ?: WEATHER_V4_WIND_DIR_DEG_UNKNOWN,
            locationName = locationName,
            forecastShort = forecastShort,
        )
    }

    override fun recordHashCode(): Int = hashCode()
}

private fun Double?.toCoordE2(): Short =
    this?.let { (it * 100).roundToInt().coerceIn(Short.MIN_VALUE + 1, Short.MAX_VALUE.toInt()).toShort() }
        ?: WEATHER_V4_COORD_UNKNOWN

class WeatherAppBlobRecord(
    version: UByte = 3u,
    currentTemp: Short,
    currentWeatherType: UByte,
    todayHighTemp: Short,
    todayLowTemp: Short,
    tomorrowWeatherType: UByte,
    tomorrowHighTemp: Short,
    tomorrowLowTemp: Short,
    lastUpdateTimeUtc: UInt,
    isCurrentLocation: Boolean,
    locationName: String,
    forecastShort: String,
) : StructMappable(endianness = Endian.Little) {
    val version = SUByte(m, version)
    val currentTemp = SShort(m, currentTemp, endianness = Endian.Little)
    val currentWeatherType = SUByte(m, currentWeatherType)
    val todayHighTemp = SShort(m, todayHighTemp, endianness = Endian.Little)
    val todayLowTemp = SShort(m, todayLowTemp, endianness = Endian.Little)
    val tomorrowWeatherType = SUByte(m, tomorrowWeatherType)
    val tomorrowHighTemp = SShort(m, tomorrowHighTemp, endianness = Endian.Little)
    val tomorrowLowTemp = SShort(m, tomorrowLowTemp, endianness = Endian.Little)
    val lastUpdateTimeUtc = SUInt(m, lastUpdateTimeUtc, endianness = Endian.Little)
    val isCurrentLocation = SBoolean(m, isCurrentLocation)
    val allStringsLength = SUShort(m, (locationName.length + 2 + forecastShort.length + 2).toUShort(), endianness = Endian.Little)
    val locationName = SLongString(m, locationName, endianness = Endian.Little)
    val forecastShort = SLongString(m, forecastShort, endianness = Endian.Little)
}

// v4 record sizing + "unknown" sentinels — must match weather_db.h.
internal const val WEATHER_DB_MAX_FORECAST_DAYS = 7
internal const val WEATHER_DB_HOURLY_COUNT = 24
internal const val WEATHER_DB_MINOR_VERSION: UByte = 3u
internal val WEATHER_V4_TEMP_UNKNOWN: Short = Short.MAX_VALUE          // 32767
internal val WEATHER_V4_UV_UNKNOWN: Short = -1
internal val WEATHER_V4_PRECIP_UNKNOWN: Short = -1
internal val WEATHER_V4_WIND_SPEED_UNKNOWN: UShort = 0u
internal val WEATHER_V4_WIND_DIR_UNKNOWN: UShort = 0xFFFFu
internal val WEATHER_V4_COORD_UNKNOWN: Short = Short.MIN_VALUE         // -32768
internal val WEATHER_V4_UTC_OFFSET_UNKNOWN: Short = Short.MIN_VALUE    // -32768
internal val WEATHER_V4_DAILY_METRIC_UNKNOWN: UByte = 0xFFu
internal val WEATHER_V4_BYTE_UNKNOWN: UByte = 0xFFu
internal val WEATHER_V4_USHORT_UNKNOWN: UShort = 0xFFFFu
internal val WEATHER_V4_WIND_DIR_DEG_UNKNOWN: Short = -1

/** One 5-byte daily forecast entry: int16 high, int16 low, uint8 weather type (255 == unknown). */
private class WeatherDailyForecastStruct(
    highTemp: Short,
    lowTemp: Short,
    weatherType: UByte,
) : StructMappable(endianness = Endian.Little) {
    val highTemp = SShort(m, highTemp, endianness = Endian.Little)
    val lowTemp = SShort(m, lowTemp, endianness = Endian.Little)
    val weatherType = SUByte(m, weatherType)
}

/** One 3-byte per-day metrics entry (v4 minor 1+), parallel to daily[]. 255 == unknown. */
private class WeatherDailyMetricsStruct(
    precipProbability: UByte,
    windSpeed: UByte,
    uvIndexX10: UByte,
) : StructMappable(endianness = Endian.Little) {
    val precipProbability = SUByte(m, precipProbability)
    val windSpeed = SUByte(m, windSpeed)
    val uvIndexX10 = SUByte(m, uvIndexX10)
}

/** Packs [WEATHER_DB_MAX_FORECAST_DAYS] little-endian int16s for the parallel per-day arrays. */
private fun dailyShortsLe(days: List<WeatherDailyForecast>, unknown: Short, value: (WeatherDailyForecast) -> Short?): UByteArray {
    val out = UByteArray(WEATHER_DB_MAX_FORECAST_DAYS * 2)
    repeat(WEATHER_DB_MAX_FORECAST_DAYS) { i ->
        val v = (days.getOrNull(i)?.let(value) ?: unknown).toInt()
        out[i * 2] = (v and 0xFF).toUByte()
        out[i * 2 + 1] = ((v shr 8) and 0xFF).toUByte()
    }
    return out
}

/** 0-255 metric byte, or the unknown sentinel when absent/out of range. */
private fun Int?.toMetricByte(): UByte =
    this?.takeIf { it in 0..254 }?.toUByte() ?: WEATHER_V4_DAILY_METRIC_UNKNOWN

private fun Int?.toUnknownableByte(): UByte =
    this?.takeIf { it in 0..254 }?.toUByte() ?: WEATHER_V4_BYTE_UNKNOWN

/** Clamps to the firmware's documented 65534 ceiling; 0xFFFF stays reserved for "unknown". */
private fun Int?.toClampedUShort(): UShort =
    this?.takeIf { it >= 0 }?.coerceAtMost(65534)?.toUShort() ?: WEATHER_V4_USHORT_UNKNOWN

/**
 * v4 weather BlobDB record, minor version 3. Layout is the v3 fixed prefix (identical offsets),
 * then the v4 fixed fields, then the trailing pstring16s — exactly as the firmware reads it. See
 * weather_db.h. The fixed block is 177 bytes; the watch rejects a minor >= 1 record any shorter,
 * so every field is always emitted (unknown ones as sentinels). The phone only sends this when
 * the watch advertises
 * [ProtocolCapsFlag.SupportsWeatherDbV4]; otherwise it keeps writing [WeatherAppBlobRecord] (v3).
 */
class WeatherAppBlobRecordV4(
    currentTemp: Short,
    currentWeatherType: UByte,
    todayHighTemp: Short,
    todayLowTemp: Short,
    tomorrowWeatherType: UByte,
    tomorrowHighTemp: Short,
    tomorrowLowTemp: Short,
    lastUpdateTimeUtc: UInt,
    isCurrentLocation: Boolean,
    todayFeelsLikeTemp: Short,
    todayUvIndexX10: Short,
    todayPrecipProbability: Short,
    todayWindSpeed: UShort,
    todayWindDirection: UShort,
    latitudeE2: Short,
    longitudeE2: Short,
    numDaily: UByte,
    daily: List<WeatherDailyForecast>,
    todayHourlyCount: UByte,
    todayHourlyWeatherType: UByteArray,
    todayHourlyTemp: ByteArray,
    locationUtcOffsetMin: Short,
    todayWindDirDeg: Short,
    locationName: String,
    forecastShort: String,
) : StructMappable(endianness = Endian.Little) {
    // --- v3-compatible fixed prefix (identical offsets to the v3 record) ---
    val version = SUByte(m, 4u)
    val currentTemp = SShort(m, currentTemp, endianness = Endian.Little)
    val currentWeatherType = SUByte(m, currentWeatherType)
    val todayHighTemp = SShort(m, todayHighTemp, endianness = Endian.Little)
    val todayLowTemp = SShort(m, todayLowTemp, endianness = Endian.Little)
    val tomorrowWeatherType = SUByte(m, tomorrowWeatherType)
    val tomorrowHighTemp = SShort(m, tomorrowHighTemp, endianness = Endian.Little)
    val tomorrowLowTemp = SShort(m, tomorrowLowTemp, endianness = Endian.Little)
    val lastUpdateTimeUtc = SUInt(m, lastUpdateTimeUtc, endianness = Endian.Little)
    val isCurrentLocation = SBoolean(m, isCurrentLocation)

    // --- v4 additions (fixed-size, appended before the trailing strings) ---
    val minorVersion = SUByte(m, WEATHER_DB_MINOR_VERSION)
    val todayFeelsLikeTemp = SShort(m, todayFeelsLikeTemp, endianness = Endian.Little)
    val todayUvIndexX10 = SShort(m, todayUvIndexX10, endianness = Endian.Little)
    val todayPrecipProbability = SShort(m, todayPrecipProbability, endianness = Endian.Little)
    val todayWindSpeed = SUShort(m, todayWindSpeed, endianness = Endian.Little)
    val todayWindDirection = SUShort(m, todayWindDirection, endianness = Endian.Little)
    val latitudeE2 = SShort(m, latitudeE2, endianness = Endian.Little)
    val longitudeE2 = SShort(m, longitudeE2, endianness = Endian.Little)
    val numDaily = SUByte(m, numDaily)
    private val daily = SFixedList(
        m,
        WEATHER_DB_MAX_FORECAST_DAYS,
        List(WEATHER_DB_MAX_FORECAST_DAYS) { i ->
            val day = daily.getOrNull(i)
            if (day == null) {
                WeatherDailyForecastStruct(WEATHER_V4_TEMP_UNKNOWN, WEATHER_V4_TEMP_UNKNOWN, WeatherType.Unknown.code.toUByte())
            } else {
                WeatherDailyForecastStruct(day.highTemp, day.lowTemp, day.weatherType.code.toUByte())
            }
        },
    ) { WeatherDailyForecastStruct(0, 0, 0u) }
    val todayHourlyCount = SUByte(m, todayHourlyCount)
    val todayHourlyWeatherType = SBytes(m, WEATHER_DB_HOURLY_COUNT, todayHourlyWeatherType, endianness = Endian.Unspecified)
    val todayHourlyTemp = SBytes(m, WEATHER_DB_HOURLY_COUNT, todayHourlyTemp.toUByteArray(), endianness = Endian.Unspecified)

    // --- v4 minor 1 additions ---
    val locationUtcOffsetMin = SShort(m, locationUtcOffsetMin, endianness = Endian.Little)
    private val dailyMetrics = SFixedList(
        m,
        WEATHER_DB_MAX_FORECAST_DAYS,
        List(WEATHER_DB_MAX_FORECAST_DAYS) { i ->
            val day = daily.getOrNull(i)
            WeatherDailyMetricsStruct(
                precipProbability = day?.precipProbability?.toInt().toMetricByte(),
                windSpeed = day?.windSpeedMph.toMetricByte(),
                uvIndexX10 = day?.uvIndexX10?.toInt().toMetricByte(),
            )
        },
    ) { WeatherDailyMetricsStruct(0u, 0u, 0u) }

    // --- v4 minor 2 additions ---
    // Today's warning-line readings, taken from day 0 of the forecast.
    val todayWmoCode = SUByte(m, daily.firstOrNull()?.wmoCode.toUnknownableByte())
    val todayHumidityPct = SUByte(m, daily.firstOrNull()?.humidityPct.toUnknownableByte())
    val todayVisibilityM = SUShort(m, daily.firstOrNull()?.visibilityM.toClampedUShort(), endianness = Endian.Little)
    val todayPrecipSumMm = SUShort(m, daily.firstOrNull()?.precipSumMm.toClampedUShort(), endianness = Endian.Little)
    val dailyFeelsLike = SBytes(
        m,
        WEATHER_DB_MAX_FORECAST_DAYS * 2,
        dailyShortsLe(daily, WEATHER_V4_TEMP_UNKNOWN) { it.feelsLikeTemp },
        endianness = Endian.Unspecified,
    )

    // --- v4 minor 3 additions ---
    val todayWindDirDeg = SShort(m, todayWindDirDeg, endianness = Endian.Little)
    val dailyWindDirDeg = SBytes(
        m,
        WEATHER_DB_MAX_FORECAST_DAYS * 2,
        dailyShortsLe(daily, WEATHER_V4_WIND_DIR_DEG_UNKNOWN) { it.windDirectionDeg?.toShort() },
        endianness = Endian.Unspecified,
    )

    // --- variable-length trailing strings (MUST stay last) ---
    val allStringsLength = SUShort(m, (locationName.length + 2 + forecastShort.length + 2).toUShort(), endianness = Endian.Little)
    val locationName = SLongString(m, locationName, endianness = Endian.Little)
    val forecastShort = SLongString(m, forecastShort, endianness = Endian.Little)
}