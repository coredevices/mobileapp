package coredevices.pebble.weather

import coredevices.util.WeatherUnit
import io.rebble.libpebblecommon.plugin.LongTextShape
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.SeriesShape
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86400L
private const val HOURS = 48

/**
 * A weather plugin's `weather/location` instance as the backend's [WeatherResponse], in [units],
 * so the watch's weather app and timeline pins are built from it exactly as from the backend.
 * Null if it lacks what those need: the current temperature and condition, and at least today's
 * and tomorrow's high, low, condition, sunrise and sunset.
 */
internal fun pluginWeatherResponse(instance: SourceInstance, units: WeatherUnit, now: Instant, phoneUtcOffsetMin: Int): WeatherResponse? {
    val p = Properties(instance)
    val temperature = p.celsius(WeatherPlugin.PROPERTY_TEMPERATURE) ?: return null
    val code = p.text(WeatherPlugin.PROPERTY_CONDITION_CODE) ?: return null
    val offset = p.number(WeatherPlugin.PROPERTY_UTC_OFFSET)?.roundToInt() ?: phoneUtcOffsetMin
    val midnight = localMidnight(now, offset)

    val highs = p.daily(WeatherPlugin.PROPERTY_DAILY_HIGH, midnight) { it.numericValue?.let { n -> celsius(n.values, n.unit) } }
    val lows = p.daily(WeatherPlugin.PROPERTY_DAILY_LOW, midnight) { it.numericValue?.let { n -> celsius(n.values, n.unit) } }
    val codes = p.daily(WeatherPlugin.PROPERTY_DAILY_CONDITION_CODE, midnight) { it.longText?.values }
    val phrases = p.daily(WeatherPlugin.PROPERTY_DAILY_CONDITION, midnight) { (it.longText ?: it.shortText)?.values }
    val chances = p.daily(WeatherPlugin.PROPERTY_DAILY_PRECIPITATION, midnight) { it.numericValue?.values }
    val sunrises = p.daily(WeatherPlugin.PROPERTY_DAILY_SUNRISE, midnight) { it.timestamp?.values }
    val sunsets = p.daily(WeatherPlugin.PROPERTY_DAILY_SUNSET, midnight) { it.timestamp?.values }
    val days = (0 until listOf(highs.size, lows.size, codes.size, sunrises.size, sunsets.size).min())
        .takeWhile { highs[it] != null && lows[it] != null && codes[it] != null && sunrises[it] != null && sunsets[it] != null }
    if (days.count() < 2) return null

    val sunrise = Instant.fromEpochSeconds(sunrises[0]!!)
    val sunset = Instant.fromEpochSeconds(sunsets[0]!!)
    val night = now < sunrise || now >= sunset
    val windSpeed = p.windSpeed(units)
    val windDirection = p.number(WeatherPlugin.PROPERTY_WIND_DIRECTION)?.roundToInt()
    val shortText = p.text(WeatherPlugin.PROPERTY_CONDITION, SourceShapeNames.SHORT_TEXT) ?: code
    val phrase = p.text(WeatherPlugin.PROPERTY_CONDITION, SourceShapeNames.LONG_TEXT) ?: shortText
    val temps = { c: Double -> c.inUnits(units) }
    val current = ConditionTemps(
        feelsLike = temps(p.celsius(WeatherPlugin.PROPERTY_FEELS_LIKE) ?: temperature),
        temp = temps(temperature),
        max24Hour = temps(p.celsius(WeatherPlugin.PROPERTY_HIGH) ?: highs[0]!!),
        min24Hour = temps(p.celsius(WeatherPlugin.PROPERTY_LOW) ?: lows[0]!!),
    )

    val forecasts = days.map { day ->
        val dayPhrase = phrases.getOrNull(day) ?: codes[day]!!
        DailyForecast(
            sunriseRaw = iso8601(Instant.fromEpochSeconds(sunrises[day]!!), offset),
            sunsetRaw = iso8601(Instant.fromEpochSeconds(sunsets[day]!!), offset),
            day = dayNight(dayPhrase, temps(highs[day]!!), iconCode(codes[day]!!, night = false)),
            night = dayNight(dayPhrase, temps(lows[day]!!), iconCode(codes[day]!!, night = true)),
            maxTemp = temps(highs[day]!!),
            minTemp = temps(lows[day]!!),
            dow = "",
            uvIndex = if (day == 0) p.number(WeatherPlugin.PROPERTY_UV_INDEX) else null,
            pop = chances.getOrNull(day)?.roundToInt() ?: if (day == 0) p.number(WeatherPlugin.PROPERTY_PRECIPITATION)?.roundToInt() else null,
            windSpeed = if (day == 0) windSpeed else null,
            windDirection = if (day == 0) windDirection else null,
            humidityPct = if (day == 0) p.number(WeatherPlugin.PROPERTY_HUMIDITY)?.roundToInt() else null,
        )
    }

    return WeatherResponse(
        fcstdaily7 = DailyForecasts(DailyForecastData(forecasts)),
        conditions = Conditions(
            ConditionsData(
                ConditionsObservation(
                    metric = current.takeIf { units != WeatherUnit.Imperial },
                    imperial = current.takeIf { units == WeatherUnit.Imperial },
                    ukHybrid = current.takeIf { units == WeatherUnit.UkHybrid },
                    phrase12Char = shortText.take(12),
                    phrase22Char = shortText.take(22),
                    phrase32Char = phrase.take(32),
                    iconCode = iconCode(code, night),
                    obs_time = now.epochSeconds,
                    windSpeed = windSpeed,
                    windDirection = windDirection,
                )
            )
        ),
        fcsthourly = p.hours(midnight, units) { at ->
            val day = ((at - midnight) / SECONDS_PER_DAY).toInt()
            val rise = sunrises.getOrNull(day)
            val set = sunsets.getOrNull(day)
            rise != null && set != null && (at < rise || at >= set)
        }?.let { HourlyForecasts(HourlyForecastData(it)) },
    )
}

private class Properties(private val instance: SourceInstance) {
    private fun shape(property: String, shape: String): JsonElement? = instance.properties[property]?.get(shape)

    fun number(property: String): Double? =
        shape(property, SourceShapeNames.NUMERIC_VALUE)?.let { Json.decodeFromJsonElement<NumericValueShape>(it).value }

    fun celsius(property: String): Double? =
        shape(property, SourceShapeNames.NUMERIC_VALUE)?.let { Json.decodeFromJsonElement<NumericValueShape>(it) }
            ?.let { celsius(listOf(it.value), it.unit).single() }

    fun text(property: String, shape: String = SourceShapeNames.LONG_TEXT): String? = when (shape) {
        SourceShapeNames.SHORT_TEXT -> shape(property, shape)?.let { Json.decodeFromJsonElement<ShortTextShape>(it).text }
        else -> shape(property, shape)?.let { Json.decodeFromJsonElement<LongTextShape>(it).text }
    }

    fun series(property: String): SeriesShape? =
        shape(property, SourceShapeNames.SERIES)?.let { Json.decodeFromJsonElement<SeriesShape>(it) }

    /**
     * A daily series' values from today on, wherever it starts: a series of anything but days
     * gives none, and one starting later than today has no today.
     */
    fun <T> daily(property: String, midnight: Long, values: (SeriesShape) -> List<T?>?): List<T?> {
        val series = series(property)?.takeIf { it.stepSec == SECONDS_PER_DAY } ?: return emptyList()
        val all = values(series) ?: return emptyList()
        val today = series.start?.let { ((midnight - it).toDouble() / SECONDS_PER_DAY).roundToInt() } ?: 0
        return if (today < 0) emptyList() else all.drop(today)
    }

    /** In what the backend sends: km/h for metric, mph otherwise. */
    fun windSpeed(units: WeatherUnit): Int? {
        val wind = shape(WeatherPlugin.PROPERTY_WIND_SPEED, SourceShapeNames.NUMERIC_VALUE)
            ?.let { Json.decodeFromJsonElement<NumericValueShape>(it) } ?: return null
        val kmh = when (wind.unit) {
            "m/s" -> wind.value * 3.6
            "mph" -> wind.value * 1.609344
            "knots" -> wind.value * 1.852
            else -> wind.value
        }
        return (if (units == WeatherUnit.Imperial) kmh / 1.609344 else kmh).roundToInt()
    }

    /** Today's and tomorrow's hours from the location's local [midnight]; a missing hour takes the nearest one there is. */
    fun hours(midnight: Long, units: WeatherUnit, isNight: (Long) -> Boolean): List<HourlyForecast>? {
        val temps = series(WeatherPlugin.PROPERTY_HOURLY_TEMPERATURE) ?: return null
        val codes = series(WeatherPlugin.PROPERTY_HOURLY_CONDITION_CODE)
        val start = temps.start ?: return null
        val step = temps.stepSec ?: return null
        val values = temps.numericValue?.let { celsius(it.values, it.unit) } ?: return null
        if (values.none { it != null }) return null
        return (0 until HOURS).map { hour ->
            val at = midnight + hour * SECONDS_PER_HOUR
            val index = ((at - start) / step).toInt()
            val code = codes?.longText?.values?.let { nearestValue(it, index) }
            HourlyForecast(
                iconCode = iconCode(code ?: "unknown", night = isNight(at)),
                temp = nearestValue(values, index)!!.inUnits(units),
            )
        }
    }
}

private fun <T> nearestValue(values: List<T?>, index: Int): T? =
    values.indices.filter { values[it] != null }.minByOrNull { abs(it - index) }?.let { values[it] }

private fun celsius(values: List<Double?>, unit: String?): List<Double?> =
    if (unit == "°F") values.map { it?.let { f -> (f - 32) * 5 / 9 } } else values

private fun Double.inUnits(units: WeatherUnit): Int =
    (if (units == WeatherUnit.Imperial) this * 9 / 5 + 32 else this).roundToInt()

private fun localMidnight(now: Instant, offsetMin: Int): Long {
    val local = now.epochSeconds + offsetMin * 60
    return local - local.mod(SECONDS_PER_DAY) - offsetMin * 60
}

private fun dayNight(phrase: String, temp: Int, iconCode: Int) = DailyDayNight(
    narrative = phrase,
    temp = temp,
    iconCode = iconCode,
    phrase12Char = phrase.take(12),
    phrase22Char = phrase.take(22),
    phrase32Char = phrase.take(32),
)

/** The shared `condition_code` vocabulary as the backend's icon codes, which [toWeatherType] maps for the watch. */
internal fun iconCode(conditionCode: String, night: Boolean): Int = when (conditionCode) {
    WeatherPlugin.ICON_SUN -> if (night) 31 else 32
    WeatherPlugin.ICON_PARTLY_CLOUDY -> if (night) 29 else 30
    WeatherPlugin.ICON_CLOUDY -> 26
    WeatherPlugin.ICON_LIGHT_RAIN -> 11
    WeatherPlugin.ICON_HEAVY_RAIN -> 12
    WeatherPlugin.ICON_LIGHT_SNOW -> 14
    WeatherPlugin.ICON_HEAVY_SNOW -> 16
    WeatherPlugin.ICON_RAIN_AND_SNOW -> 5
    else -> 44
}

/** As the backend writes it: `2026-10-27T07:12:00+0100`, local time with a colon-less offset. */
internal fun iso8601(instant: Instant, offsetMin: Int): String {
    val local = instant.toLocalDateTime(UtcOffset(minutes = offsetMin).asTimeZone())
    fun two(n: Int) = n.toString().padStart(2, '0')
    val sign = if (offsetMin < 0) "-" else "+"
    val off = abs(offsetMin)
    return "${local.date}T${two(local.hour)}:${two(local.minute)}:${two(local.second)}$sign${two(off / 60)}${two(off % 60)}"
}
