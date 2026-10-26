package coredevices.pebble.weather

import coredevices.database.WeatherLocationDao
import coredevices.database.WeatherLocationEntity
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.Weather
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.plugin.BitmapSeries
import io.rebble.libpebblecommon.plugin.IconShape
import io.rebble.libpebblecommon.plugin.ImageShape
import io.rebble.libpebblecommon.plugin.LongTextShape
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.NumericSeries
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import io.rebble.libpebblecommon.plugin.SeriesShape
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.TextSeries
import io.rebble.libpebblecommon.weather.WeatherType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The condition as one of a fixed set of names — the vocabulary `condition_code` publishes and
 * the built-in glyphs are keyed by. Anything this doesn't know about is `unknown` rather than
 * a name a consumer has no artwork for.
 */
internal fun conditionCodeOf(code: Byte) = when (WeatherType.entries.firstOrNull { it.code == code }) {
    WeatherType.Sun -> WeatherPlugin.ICON_SUN
    WeatherType.PartlyCloudy -> WeatherPlugin.ICON_PARTLY_CLOUDY
    WeatherType.CloudyDay -> WeatherPlugin.ICON_CLOUDY
    WeatherType.LightRain -> WeatherPlugin.ICON_LIGHT_RAIN
    WeatherType.HeavyRain -> WeatherPlugin.ICON_HEAVY_RAIN
    WeatherType.LightSnow -> WeatherPlugin.ICON_LIGHT_SNOW
    WeatherType.HeavySnow -> WeatherPlugin.ICON_HEAVY_SNOW
    WeatherType.RainAndSnow -> WeatherPlugin.ICON_RAIN_AND_SNOW
    else -> WeatherPlugin.ICON_UNKNOWN
}

/**
 * Built-in weather plugin. One instance per saved location in the user's chosen order, reading
 * whatever [WeatherFetcher] last wrote to the weather blobdb — no fetching of its own, so
 * subscribing never triggers network traffic.
 */
class WeatherPlugin(
    private val weather: Weather,
    private val weatherLocationDao: WeatherLocationDao,
    private val libPebble: LibPebble,
) : NativePlugin {

    override val pluginUuid: Uuid = BUILT_IN_WEATHER_UUID
    override val name: String = "Weather"
    override val description: String = "Current conditions and forecast for the user's saved weather locations."

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_LOCATION),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_TEMPERATURE to TEMPERATURE_SHAPES,
                PROPERTY_FEELS_LIKE to TEMPERATURE_SHAPES,
                PROPERTY_HIGH to TEMPERATURE_SHAPES,
                PROPERTY_LOW to TEMPERATURE_SHAPES,
                PROPERTY_CONDITION to TEXT_SHAPES + CONDITION_ICON_SHAPES,
                PROPERTY_CONDITION_CODE to listOf(SourceShapeNames.LONG_TEXT),
                PROPERTY_UV_INDEX to listOf(
                    SourceShapeNames.NUMERIC_VALUE,
                    SourceShapeNames.SHORT_TEXT,
                ),
                PROPERTY_PRECIPITATION to listOf(
                    SourceShapeNames.NUMERIC_VALUE,
                    SourceShapeNames.SHORT_TEXT,
                ),
                PROPERTY_HOURLY_TEMPERATURE to NUMERIC_SERIES_SHAPES,
                PROPERTY_HOURLY_CONDITION to CONDITION_SERIES_SHAPES,
                PROPERTY_HOURLY_CONDITION_CODE to CODE_SERIES_SHAPES,
                PROPERTY_DAILY_HIGH to NUMERIC_SERIES_SHAPES,
                PROPERTY_DAILY_LOW to NUMERIC_SERIES_SHAPES,
                PROPERTY_DAILY_CONDITION to CONDITION_SERIES_SHAPES,
                PROPERTY_DAILY_CONDITION_CODE to CODE_SERIES_SHAPES,
                PROPERTY_DAILY_PRECIPITATION to NUMERIC_SERIES_SHAPES,
            ),
            supportsMultiple = true,
            callerPermissions = listOf(PluginPermission(PluginPermissions.LOCATION)),
            suggestedRefreshIntervalSec = 900,
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        val wanted = { property: String -> properties == null || property in properties }
        return combine(
            weather.currentWeather,
            weatherLocationDao.getAllLocationsFlow(),
            libPebble.healthSettings,
        ) { entries, locations, healthSettings ->
            val unit = if (healthSettings.imperialUnits) "°F" else "°C"
            toEnvelope(entries.inLocationOrder(locations), iconPixelSize, unit, wanted)
        }
    }

    private fun toEnvelope(
        entries: List<WeatherAppEntry>,
        iconSize: IconPixelSize?,
        unit: String,
        wanted: (String) -> Boolean,
    ) = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = entries.map { entry -> toInstance(entry, iconSize, unit, wanted) },
    )

    private fun toInstance(
        entry: WeatherAppEntry,
        iconSize: IconPixelSize?,
        unit: String,
        wanted: (String) -> Boolean,
    ): SourceInstance {
        return SourceInstance(
            instanceId = entry.key.toString(),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to mapOf(
                    SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(entry.locationName)),
                ),
                PROPERTY_TEMPERATURE to tempShapes(entry.currentTemp, entry.locationName, unit),
                PROPERTY_FEELS_LIKE to tempShapes(
                    entry.todayFeelsLikeTemp ?: entry.currentTemp,
                    entry.locationName,
                    unit,
                ),
                PROPERTY_HIGH to tempShapes(entry.todayHighTemp, entry.locationName, unit),
                PROPERTY_LOW to tempShapes(entry.todayLowTemp, entry.locationName, unit),
                PROPERTY_CONDITION to conditionShapes(
                    entry.currentWeatherType,
                    entry.forecastShort,
                    entry.locationName,
                    iconSize,
                ),
                PROPERTY_CONDITION_CODE to conditionCode(entry.currentWeatherType),
            ) + uvShapes(entry) + precipitationShapes(entry) + seriesShapes(entry, iconSize, unit, wanted),
        )
    }

    /**
     * Today's and tomorrow's hours, and the days ahead, from local midnight on the day the forecast
     * was fetched: `start` keeps them true however long ago that was. Built only when asked for,
     * since a series of glyphs is the most expensive thing an emission can carry.
     */
    private fun seriesShapes(
        entry: WeatherAppEntry,
        iconSize: IconPixelSize?,
        unit: String,
        wanted: (String) -> Boolean,
    ): Map<String, Map<String, JsonElement>> = buildMap {
        val start = entry.forecastDayStart()
        val hours = entry.todayHourly.orEmpty() + entry.tomorrowHourly.orEmpty()
        val days = entry.dailyForecast.orEmpty()
        fun series(property: String, values: List<*>, step: Long, build: SeriesShape.() -> SeriesShape) {
            if (values.isNotEmpty() && wanted(property)) {
                put(property, mapOf(SourceShapeNames.SERIES to encode(SeriesShape(start, step).build())))
            }
        }
        series(PROPERTY_HOURLY_TEMPERATURE, hours, SECONDS_PER_HOUR) {
            temperatureSeries(hours.map { it.temp.toShort() }, unit)
        }
        series(PROPERTY_HOURLY_CONDITION, hours, SECONDS_PER_HOUR) {
            conditionSeries(hours.map { it.weatherType.code }, iconSize)
        }
        series(PROPERTY_HOURLY_CONDITION_CODE, hours, SECONDS_PER_HOUR) {
            copy(longText = TextSeries(hours.map { conditionCodeOf(it.weatherType.code) }))
        }
        series(PROPERTY_DAILY_HIGH, days, SECONDS_PER_DAY) {
            temperatureSeries(days.map { it.highTemp.takeUnless { temp -> temp == TEMP_NO_VALUE } }, unit)
        }
        series(PROPERTY_DAILY_LOW, days, SECONDS_PER_DAY) {
            temperatureSeries(days.map { it.lowTemp }, unit)
        }
        series(PROPERTY_DAILY_CONDITION, days, SECONDS_PER_DAY) {
            conditionSeries(days.map { it.weatherType.code }, iconSize)
        }
        series(PROPERTY_DAILY_CONDITION_CODE, days, SECONDS_PER_DAY) {
            copy(longText = TextSeries(days.map { conditionCodeOf(it.weatherType.code) }))
        }
        series(PROPERTY_DAILY_PRECIPITATION, days, SECONDS_PER_DAY) {
            val percents = days.map { it.precipProbability?.toDouble() }
            copy(
                numericValue = NumericSeries(percents, unit = "%", min = 0.0, max = 100.0),
                shortText = TextSeries(percents.map { it?.let { percent -> "${percent.roundToInt()}%" } }),
            )
        }
    }

    private fun SeriesShape.temperatureSeries(temps: List<Short?>, unit: String) = copy(
        numericValue = NumericSeries(temps.map { it?.toDouble() }, unit = unit),
        shortText = TextSeries(temps.map { it?.let { temp -> "$temp$unit" } }),
    )

    private fun SeriesShape.conditionSeries(codes: List<Byte>, iconSize: IconPixelSize?) = copy(
        shortText = TextSeries(codes.map(::conditionName)),
        icon = iconSize?.let { size -> BitmapSeries(codes.map { iconShape(it, size) }) },
        image = iconSize?.let { size -> BitmapSeries(codes.map { imageShape(it, size) }) },
    )

    /** Also v4-only; absent rather than zero when the forecast didn't carry one. */
    private fun precipitationShapes(entry: WeatherAppEntry): Map<String, Map<String, JsonElement>> {
        val percent = entry.todayPrecipProbability?.toDouble() ?: return emptyMap()
        return mapOf(
            PROPERTY_PRECIPITATION to mapOf(
                SourceShapeNames.NUMERIC_VALUE to encode(
                    NumericValueShape(value = percent, unit = "%", min = 0.0, max = 100.0)
                ),
                SourceShapeNames.SHORT_TEXT to
                    encode(ShortTextShape("${percent.roundToInt()}%")),
            ),
        )
    }

    /** v4-only, so a watch that never reported one leaves the property off the instance. */
    private fun uvShapes(entry: WeatherAppEntry): Map<String, Map<String, JsonElement>> {
        val uv = entry.todayUvIndexX10?.let { it / 10.0 } ?: return emptyMap()
        return mapOf(
            PROPERTY_UV_INDEX to mapOf(
                // 11 is the top of the published scale; anything above it is still "extreme".
                SourceShapeNames.NUMERIC_VALUE to
                    encode(NumericValueShape(value = uv, min = 0.0, max = 11.0)),
                SourceShapeNames.SHORT_TEXT to
                    encode(ShortTextShape(uv.roundToInt().toString())),
            ),
        )
    }

    // The numericValue carries no min or max: a temperature has nothing to fill a gauge against,
    // but a consumer that wants the number rather than the string still gets it.
    private fun tempShapes(temp: Short, locationName: String, unit: String) = mapOf(
        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape("$temp$unit")),
        SourceShapeNames.LONG_TEXT to encode(LongTextShape("$temp$unit")),
        SourceShapeNames.NUMERIC_VALUE to
            encode(NumericValueShape(value = temp.toDouble(), unit = unit)),
    )

    /**
     * The glyphs are only drawn when a subscriber asked for them: rasterising and encoding is
     * the expensive half of an emission, and a tile showing the word doesn't want them.
     */
    private fun conditionShapes(
        code: Byte,
        forecast: String?,
        locationName: String?,
        iconSize: IconPixelSize?,
    ) = buildMap {
        put(SourceShapeNames.SHORT_TEXT, encode(ShortTextShape(conditionName(code))))
        forecast?.let { put(SourceShapeNames.LONG_TEXT, encode(LongTextShape(it))) }
        iconSize?.let { size ->
            put(SourceShapeNames.ICON, encode(iconShape(code, size)))
            put(SourceShapeNames.IMAGE, encode(imageShape(code, size)))
        }
    }

    private fun iconShape(code: Byte, size: IconPixelSize): IconShape {
        val icon = WeatherIcons.icon(conditionCodeOf(code), minOf(size.w, size.h))
        return IconShape(icon.base64Pixels(), icon.base64Palette(), icon.width, icon.height)
    }

    private fun imageShape(code: Byte, size: IconPixelSize): ImageShape {
        val image = WeatherIcons.image(conditionCodeOf(code), minOf(size.w, size.h))
        return ImageShape(image.base64Pixels(), image.base64Palette(), image.width, image.height)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun EncodedImage.base64Pixels() = Base64.encode(pixels.toByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    private fun EncodedImage.base64Palette() = Base64.encode(palette.toByteArray())

    /**
     * The condition as one of a fixed set of names, for a consumer that would rather draw its
     * own artwork than take the plugin's glyph. The same vocabulary the built-in glyphs are
     * keyed by, so a plugin serving weather from somewhere else can be swapped in behind a
     * watchface that ships a picture per code.
     */
    // longText, not shortText: `partly_cloudy` is twice the few characters shortText promises.
    private fun conditionCode(code: Byte) =
        mapOf(SourceShapeNames.LONG_TEXT to encode(LongTextShape(conditionCodeOf(code))))

    private fun conditionName(code: Byte) = when (WeatherType.entries.firstOrNull { it.code == code }) {
        WeatherType.PartlyCloudy -> "Partly cloudy"
        WeatherType.CloudyDay -> "Cloudy"
        WeatherType.LightSnow -> "Light snow"
        WeatherType.LightRain -> "Light rain"
        WeatherType.HeavyRain -> "Heavy rain"
        WeatherType.HeavySnow -> "Heavy snow"
        WeatherType.Sun -> "Sunny"
        WeatherType.RainAndSnow -> "Rain and snow"
        else -> "Unknown"
    }

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "weather"
        const val ITEM_LOCATION = "location"
        const val PROPERTY_TEMPERATURE = "temperature"
        const val PROPERTY_FEELS_LIKE = "feels_like"
        const val PROPERTY_HIGH = "high"
        const val PROPERTY_LOW = "low"
        const val PROPERTY_CONDITION = "condition"
        const val PROPERTY_CONDITION_CODE = "condition_code"
        const val PROPERTY_UV_INDEX = "uv_index"
        const val PROPERTY_PRECIPITATION = "precipitation"
        const val PROPERTY_HOURLY_TEMPERATURE = "hourly_temperature"
        const val PROPERTY_HOURLY_CONDITION = "hourly_condition"
        const val PROPERTY_HOURLY_CONDITION_CODE = "hourly_condition_code"
        const val PROPERTY_DAILY_HIGH = "daily_high"
        const val PROPERTY_DAILY_LOW = "daily_low"
        const val PROPERTY_DAILY_CONDITION = "daily_condition"
        const val PROPERTY_DAILY_CONDITION_CODE = "daily_condition_code"
        const val PROPERTY_DAILY_PRECIPITATION = "daily_precipitation"

        // The weather icon vocabulary, published in `new-plugin-api.md` for consumers to draw.
        const val ICON_SUN = "sun"
        const val ICON_PARTLY_CLOUDY = "partly_cloudy"
        const val ICON_CLOUDY = "cloudy"
        const val ICON_LIGHT_RAIN = "light_rain"
        const val ICON_HEAVY_RAIN = "heavy_rain"
        const val ICON_LIGHT_SNOW = "light_snow"
        const val ICON_HEAVY_SNOW = "heavy_snow"
        const val ICON_RAIN_AND_SNOW = "rain_and_snow"
        const val ICON_UNKNOWN = "unknown"

        private val TEXT_SHAPES =
            listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.LONG_TEXT)
        private val CONDITION_ICON_SHAPES =
            listOf(SourceShapeNames.ICON, SourceShapeNames.IMAGE)
        private val TEMPERATURE_SHAPES = TEXT_SHAPES + SourceShapeNames.NUMERIC_VALUE
        private val NUMERIC_SERIES_SHAPES =
            listOf(SourceShapeNames.NUMERIC_VALUE, SourceShapeNames.SHORT_TEXT).map(SourceShapeNames::series)
        private val CONDITION_SERIES_SHAPES =
            listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.ICON, SourceShapeNames.IMAGE).map(SourceShapeNames::series)
        private val CODE_SERIES_SHAPES = listOf(SourceShapeNames.series(SourceShapeNames.LONG_TEXT))

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_WEATHER_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000002")
    }
}

/**
 * Local midnight, in epoch seconds, on the day the forecast was fetched: where its hourly and daily
 * series start. The location's own offset if it reported one; the phone's otherwise, since the
 * location that matters is usually the user's own.
 */
internal fun WeatherAppEntry.forecastDayStart(): Long {
    val offsetSec = locationUtcOffsetMin?.let { it * 60L }
        ?: TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochSeconds(lastUpdateTimeUtcSecs)).totalSeconds.toLong()
    val local = lastUpdateTimeUtcSecs + offsetSec
    return local - local.mod(SECONDS_PER_DAY) - offsetSec
}

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86_400L

/** The blobdb has no ordering of its own; the user's is in the app's location table. */
internal fun List<WeatherAppEntry>.inLocationOrder(
    locations: List<WeatherLocationEntity>,
): List<WeatherAppEntry> {
    val order = locations.withIndex().associate { (index, location) -> location.key to index }
    return sortedBy { order[it.key] ?: Int.MAX_VALUE }
}
