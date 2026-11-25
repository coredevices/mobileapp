package coredevices.ring.agent.builtin_servlets.plugins

import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/**
 * Times rewritten for a language model: every point of a time series keyed by the local
 * wall-clock time it belongs to, and every bare timestamp spelled out, so the model quotes a time
 * instead of computing one from `start` + index + offset. Series without `start`/`stepSec` are
 * positional and pass through as they are.
 */
internal fun SourceInstance.renderTimesForModel(zone: TimeZone): SourceInstance {
    val localZone = utcOffsetZone() ?: zone
    return copy(
        properties = properties.mapValues { (_, shapes) ->
            shapes.mapValues { (shape, payload) ->
                when (shape) {
                    SourceShapeNames.SERIES -> renderSeries(payload, localZone)
                    SourceShapeNames.TIMESTAMP -> renderTimestamp(payload, localZone)
                    else -> payload
                }
            }
        },
    )
}

// The shared `weather/location` property; a place's own offset beats the phone's zone.
private const val PROPERTY_UTC_OFFSET = "utc_offset"
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_MINUTE = 60L

private fun SourceInstance.utcOffsetZone(): TimeZone? {
    val minutes = properties[PROPERTY_UTC_OFFSET]?.get(SourceShapeNames.NUMERIC_VALUE)
        ?.objectOrNull()?.get("value")?.primitiveOrNull()?.doubleOrNull ?: return null
    val offset = runCatching { UtcOffset(seconds = (minutes * SECONDS_PER_MINUTE).toInt()) }
        .getOrNull() ?: return null
    return FixedOffsetTimeZone(offset)
}

private fun renderSeries(payload: JsonElement, zone: TimeZone): JsonElement {
    val series = payload.objectOrNull() ?: return payload
    val start = series["start"]?.primitiveOrNull()?.longOrNull ?: return payload
    val step = series["stepSec"]?.primitiveOrNull()?.longOrNull?.takeIf { it > 0 } ?: return payload
    val values = series.pointValues(zone) ?: return payload
    val label = labelFormat(step)
    return JsonObject(
        values.withIndex().associate { (index, value) ->
            label(Instant.fromEpochSeconds(start + index * step).toLocalDateTime(zone)) to value
        },
    )
}

/** One reading per point: the first of the series' shapes a model can read as text. */
private fun JsonObject.pointValues(zone: TimeZone): List<JsonElement>? {
    textValues(SourceShapeNames.SHORT_TEXT)?.let { return it }
    textValues(SourceShapeNames.LONG_TEXT)?.let { return it }
    this[SourceShapeNames.NUMERIC_VALUE]?.objectOrNull()?.let { numeric ->
        val unit = numeric["unit"]?.primitiveOrNull()?.contentOrNull
        return numeric.values()?.map { point ->
            point.primitiveOrNull()?.doubleOrNull?.let { JsonPrimitive(formatNumber(it, unit)) } ?: JsonNull
        }
    }
    this[SourceShapeNames.TIMESTAMP]?.objectOrNull()?.let { timestamps ->
        return timestamps.values()?.map { point -> renderTimestampValue(point, zone) }
    }
    return null
}

private fun JsonObject.textValues(shape: String): List<JsonElement>? =
    this[shape]?.objectOrNull()?.values()?.map { it.primitiveOrNull() ?: JsonNull }

private fun JsonObject.values(): List<JsonElement>? = (this["values"] as? JsonArray)?.toList()

private fun renderTimestamp(payload: JsonElement, zone: TimeZone): JsonElement =
    payload.objectOrNull()?.get("value")?.let { renderTimestampValue(it, zone) } ?: payload

private fun renderTimestampValue(point: JsonElement, zone: TimeZone): JsonElement {
    val epochSeconds = point.primitiveOrNull()?.longOrNull ?: return JsonNull
    val local = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
    val clock = if (local.second == 0) local.hhmm() else local.hhmmss()
    return JsonPrimitive("${local.dayAndDate()} $clock")
}

/** Day steps read as dates, minute-aligned steps to the minute, anything else to the second. */
private fun labelFormat(stepSec: Long): (LocalDateTime) -> String = when {
    stepSec % SECONDS_PER_DAY == 0L -> { local -> local.dayAndDate() }
    stepSec % SECONDS_PER_MINUTE == 0L -> { local -> "${local.dayAndDate()} ${local.hhmm()}" }
    else -> { local -> "${local.dayAndDate()} ${local.hhmmss()}" }
}

private fun LocalDateTime.dayAndDate(): String {
    val day = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$day $date"
}

private fun LocalDateTime.hhmm() = "${hour.pad()}:${minute.pad()}"

private fun LocalDateTime.hhmmss() = "${hhmm()}:${second.pad()}"

private fun Int.pad() = toString().padStart(2, '0')

private fun formatNumber(value: Double, unit: String?): String {
    val number = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    return when {
        unit.isNullOrEmpty() -> number
        unit.first().isLetterOrDigit() -> "$number $unit"
        else -> "$number$unit"
    }
}

private fun JsonElement.objectOrNull() = this as? JsonObject

private fun JsonElement.primitiveOrNull() = this as? JsonPrimitive
