package coredevices.pebble.weather

import coredevices.database.WeatherLocationDao
import coredevices.database.WeatherLocationEntity
import coredevices.util.CoreConfigFlow
import coredevices.util.WeatherUnit
import io.rebble.libpebblecommon.connection.Weather
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.plugin.IconShape
import io.rebble.libpebblecommon.plugin.ImageShape
import io.rebble.libpebblecommon.plugin.LongTextShape
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.Plugin
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.weather.WeatherHourlyForecast
import io.rebble.libpebblecommon.weather.WeatherType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt
import kotlin.time.Clock
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
    private val coreConfigFlow: CoreConfigFlow,
    private val clock: Clock,
) : Plugin {

    override val pluginUuid: Uuid = BUILT_IN_WEATHER_UUID
    override val name: String = "Weather"

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
            ),
            supportsMultiple = true,
            usesPermissions = listOf(PluginPermission("Location")),
            suggestedRefreshIntervalSec = 900,
        ),
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_HOUR),
            properties = mapOf(
                PROPERTY_TEMPERATURE to listOf(
                    SourceShapeNames.NUMERIC_VALUE,
                    SourceShapeNames.SHORT_TEXT,
                ),
                PROPERTY_CONDITION to listOf(SourceShapeNames.SHORT_TEXT) +
                    CONDITION_ICON_SHAPES,
                PROPERTY_CONDITION_CODE to listOf(SourceShapeNames.LONG_TEXT),
            ),
            supportsMultiple = true,
            usesPermissions = listOf(PluginPermission("Location")),
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
        return combine(
            weather.currentWeather,
            weatherLocationDao.getAllLocationsFlow(),
        ) { entries, locations ->
            val ordered = entries.inLocationOrder(locations)
            if (item == ITEM_HOUR) {
                hourlyEnvelope(ordered.firstOrNull(), iconPixelSize)
            } else {
                toEnvelope(ordered, iconPixelSize)
            }
        }
    }

    /**
     * The hours ahead, soonest first, for the user's first saved location — the forecast has no
     * per-hour timestamp, so position is all an instance id can be.
     */
    private fun hourlyEnvelope(entry: WeatherAppEntry?, iconSize: IconPixelSize?) = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = entry.hoursAhead(localHour(entry)).take(MAX_HOURS).mapIndexed { index, hour ->
            val unit = degreeSymbol()
            SourceInstance(
                instanceId = index.toString(),
                properties = mapOf(
                    PROPERTY_TEMPERATURE to mapOf(
                        SourceShapeNames.NUMERIC_VALUE to
                            encode(NumericValueShape(value = hour.temp.toDouble(), unit = unit)),
                        SourceShapeNames.SHORT_TEXT to
                            encode(ShortTextShape("${hour.temp}$unit")),
                    ),
                    PROPERTY_CONDITION to
                        conditionShapes(hour.weatherType.code, null, null, iconSize),
                    PROPERTY_CONDITION_CODE to conditionCode(hour.weatherType.code),
                ),
            )
        },
    )

    private fun toEnvelope(entries: List<WeatherAppEntry>, iconSize: IconPixelSize?) =
        SourceEnvelope(
            pluginUuid = pluginUuid.toString(),
            validUntilMs = null,
            instances = entries.map { entry -> toInstance(entry, iconSize) },
        )

    private fun toInstance(entry: WeatherAppEntry, iconSize: IconPixelSize?): SourceInstance {
        val unit = degreeSymbol()
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
            ) + uvShapes(entry) + precipitationShapes(entry),
        )
    }

    /**
     * The hour it is where the forecast is, since that is what its series is indexed by. The
     * location's own offset if it reported one; the phone's own clock is the better guess than
     * UTC when it didn't, because the location that matters is usually the user's own.
     */
    private fun localHour(entry: WeatherAppEntry?): Int {
        val now = clock.now()
        val offsetMinutes = entry?.locationUtcOffsetMin
            ?: return now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return (((now.epochSeconds + offsetMinutes * 60) / SECONDS_PER_HOUR) % 24).toInt()
    }

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
            val name = conditionCodeOf(code)
            val pixels = minOf(size.w, size.h)
            val icon = WeatherIcons.icon(name, pixels)
            put(
                SourceShapeNames.ICON,
                encode(IconShape(icon.base64Pixels(), icon.base64Palette(), icon.width, icon.height)),
            )
            val image = WeatherIcons.image(name, pixels)
            put(
                SourceShapeNames.IMAGE,
                encode(ImageShape(image.base64Pixels(), image.base64Palette(), image.width, image.height)),
            )
        }
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

    private fun isMetric() = coreConfigFlow.value.weatherUnits != WeatherUnit.Imperial

    private fun degreeSymbol() = if (isMetric()) "°C" else "°F"

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "weather"
        const val ITEM_LOCATION = "location"
        const val ITEM_HOUR = "hour"
        const val PROPERTY_TEMPERATURE = "temperature"
        const val PROPERTY_FEELS_LIKE = "feels_like"
        const val PROPERTY_HIGH = "high"
        const val PROPERTY_LOW = "low"
        const val PROPERTY_CONDITION = "condition"
        const val PROPERTY_CONDITION_CODE = "condition_code"
        const val PROPERTY_UV_INDEX = "uv_index"
        const val PROPERTY_PRECIPITATION = "precipitation"

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
        private const val MAX_HOURS = 6
        private const val SECONDS_PER_HOUR = 3600

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_WEATHER_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000002")
    }
}

/**
 * The hourly series starts at the location's local midnight, so "the next few hours" means
 * skipping the ones already gone — and rolling into tomorrow's series once today runs out.
 */
internal fun WeatherAppEntry?.hoursAhead(localHour: Int): List<WeatherHourlyForecast> {
    if (this == null) return emptyList()
    return todayHourly.orEmpty().drop(localHour + 1) + tomorrowHourly.orEmpty()
}

/** The blobdb has no ordering of its own; the user's is in the app's location table. */
internal fun List<WeatherAppEntry>.inLocationOrder(
    locations: List<WeatherLocationEntity>,
): List<WeatherAppEntry> {
    val order = locations.withIndex().associate { (index, location) -> location.key to index }
    return sortedBy { order[it.key] ?: Int.MAX_VALUE }
}
