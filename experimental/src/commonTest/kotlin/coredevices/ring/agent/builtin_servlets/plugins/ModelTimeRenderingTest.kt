package coredevices.ring.agent.builtin_servlets.plugins

import io.rebble.libpebblecommon.plugin.NumericSeries
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.SeriesShape
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.TextSeries
import io.rebble.libpebblecommon.plugin.TimestampSeries
import io.rebble.libpebblecommon.plugin.TimestampShape
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

// Local midnight, Mon 2026-11-02, in Los Angeles (PST, UTC-8).
private const val LA_MIDNIGHT = 1793606400L
private const val HOUR = 3600L
private const val DAY = 86_400L

private val utc = TimeZone.UTC
private val losAngeles = TimeZone.of("America/Los_Angeles")

private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

private fun series(shape: SeriesShape) = mapOf(SourceShapeNames.SERIES to encode(shape))

private fun offsetMinutes(minutes: Double) =
    mapOf(SourceShapeNames.NUMERIC_VALUE to encode(NumericValueShape(minutes, "min")))

private fun instance(vararg properties: Pair<String, Map<String, JsonElement>>) =
    SourceInstance("id", properties.toMap())

private fun SourceInstance.series(property: String): JsonObject =
    properties.getValue(property).getValue(SourceShapeNames.SERIES).jsonObject

private fun JsonObject.texts() = mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull() }

private fun JsonPrimitive.contentOrNull() = if (this is JsonNull) null else content

class ModelTimeRenderingTest {

    @Test
    fun hourlySeriesIsKeyedByLocalTimeOfTheInstanceOffset() {
        val rendered = instance(
            "utc_offset" to offsetMinutes(-480.0),
            "hourly_condition" to series(
                SeriesShape(
                    start = LA_MIDNIGHT + 35 * HOUR,
                    stepSec = HOUR,
                    shortText = TextSeries(listOf("Light rain", "Cloudy", null)),
                ),
            ),
        ).renderTimesForModel(utc)

        assertEquals(
            mapOf(
                "Tuesday 2026-11-03 11:00" to "Light rain",
                "Tuesday 2026-11-03 12:00" to "Cloudy",
                "Tuesday 2026-11-03 13:00" to null,
            ),
            rendered.series("hourly_condition").texts(),
        )
    }

    @Test
    fun fallsBackToTheGivenZoneWithoutAnOffset() {
        val rendered = instance(
            "hourly_condition" to series(
                SeriesShape(start = LA_MIDNIGHT, stepSec = HOUR, shortText = TextSeries(listOf("Sunny"))),
            ),
        ).renderTimesForModel(losAngeles)

        assertEquals(setOf("Monday 2026-11-02 00:00"), rendered.series("hourly_condition").keys)
    }

    @Test
    fun dailyStepsReadAsDates() {
        val rendered = instance(
            "utc_offset" to offsetMinutes(-480.0),
            "daily_precipitation" to series(
                SeriesShape(
                    start = LA_MIDNIGHT,
                    stepSec = DAY,
                    numericValue = NumericSeries(listOf(2.0, 50.0), unit = "%", min = 0.0, max = 100.0),
                    shortText = TextSeries(listOf("2%", "50%")),
                ),
            ),
        ).renderTimesForModel(utc)

        assertEquals(
            mapOf("Monday 2026-11-02" to "2%", "Tuesday 2026-11-03" to "50%"),
            rendered.series("daily_precipitation").texts(),
        )
    }

    @Test
    fun unrecognisedStepsReadToTheSecond() {
        val rendered = instance(
            "readings" to series(
                SeriesShape(start = LA_MIDNIGHT, stepSec = 90, shortText = TextSeries(listOf("a", "b"))),
            ),
        ).renderTimesForModel(utc)

        assertEquals(
            listOf("Monday 2026-11-02 08:00:00", "Monday 2026-11-02 08:01:30"),
            rendered.series("readings").keys.toList(),
        )
    }

    @Test
    fun numericSeriesWithoutTextCarriesItsUnit() {
        val rendered = instance(
            "hourly_wind" to series(
                SeriesShape(
                    start = LA_MIDNIGHT,
                    stepSec = HOUR,
                    numericValue = NumericSeries(listOf(3.0, 4.5, null), unit = "mph"),
                ),
            ),
            "hourly_temperature" to series(
                SeriesShape(start = LA_MIDNIGHT, stepSec = HOUR, numericValue = NumericSeries(listOf(13.0), unit = "°C")),
            ),
        ).renderTimesForModel(utc)

        assertEquals(listOf("3 mph", "4.5 mph", null), rendered.series("hourly_wind").texts().values.toList())
        assertEquals(listOf("13°C"), rendered.series("hourly_temperature").texts().values.toList())
    }

    @Test
    fun timestampSeriesAndBareTimestampsAreSpelledOut() {
        val sunrise = LA_MIDNIGHT + 6 * HOUR + 34 * 60
        val rendered = instance(
            "utc_offset" to offsetMinutes(-480.0),
            "sunrise" to mapOf(SourceShapeNames.TIMESTAMP to encode(TimestampShape(sunrise))),
            "daily_sunrise" to series(
                SeriesShape(start = LA_MIDNIGHT, stepSec = DAY, timestamp = TimestampSeries(listOf(sunrise, sunrise + DAY + 5))),
            ),
        ).renderTimesForModel(utc)

        assertEquals(
            "Monday 2026-11-02 06:34",
            rendered.properties.getValue("sunrise").getValue(SourceShapeNames.TIMESTAMP).jsonPrimitive.content,
        )
        assertEquals(
            mapOf("Monday 2026-11-02" to "Monday 2026-11-02 06:34", "Tuesday 2026-11-03" to "Tuesday 2026-11-03 06:34:05"),
            rendered.series("daily_sunrise").texts(),
        )
    }

    @Test
    fun positionalSeriesAndOtherShapesPassThrough() {
        val positional = series(SeriesShape(shortText = TextSeries(listOf("x", "y"))))
        val temperature = mapOf(SourceShapeNames.SHORT_TEXT to encode(ShortTextShape("13°C")))
        val original = instance("steps" to positional, "temperature" to temperature)

        assertEquals(original, original.renderTimesForModel(utc))
    }
}
