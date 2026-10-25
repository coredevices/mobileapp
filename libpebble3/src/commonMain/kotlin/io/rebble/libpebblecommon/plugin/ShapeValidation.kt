package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Not a reason to drop anything: a consumer can still draw it, if not in the room it planned. */
internal const val SHORT_TEXT_GUIDE_LENGTH = 7

private const val MAX_PALETTE_SIZE = 16

/**
 * The envelope with every shape a consumer couldn't safely read taken out: shapes the source
 * doesn't declare, and payloads that don't match their shape. A missing shape already means "can't
 * serve it", so consumers handle what's left. [report] hears about each, and about a too-long
 * shortText, which is kept.
 */
internal fun SourceEnvelope.validated(
    declaration: SourceDeclaration,
    iconPixelSize: IconPixelSize?,
    report: (property: String, problem: String) -> Unit,
): SourceEnvelope = copy(
    instances = instances.map { instance ->
        instance.copy(
            properties = instance.properties.mapNotNull { (property, shapes) ->
                val declared = declaration.properties[property].orEmpty()
                val kept = shapes.mapNotNull { (shape, payload) ->
                    val problem = if (shape == SourceShapeNames.SERIES) {
                        seriesProblem(payload, declared.seriesShapes(), iconPixelSize) { report(property, it) }
                    } else if (shape !in declared) {
                        "$shape isn't declared"
                    } else {
                        shapeProblem(shape, payload, iconPixelSize) { report(property, it) }
                    }
                    problem?.let { report(property, "dropped: $it") }
                    (shape to payload).takeIf { problem == null }
                }.toMap()
                (property to kept).takeIf { kept.isNotEmpty() }
            }.toMap(),
        )
    },
)

private fun List<String>.seriesShapes(): Set<String> =
    mapNotNull { it.removePrefix("${SourceShapeNames.SERIES}.").takeIf { shape -> shape != it } }.toSet()

private fun shapeProblem(
    shape: String,
    payload: JsonElement,
    iconPixelSize: IconPixelSize?,
    warn: (String) -> Unit,
): String? {
    val fields = payload as? JsonObject ?: return "$shape isn't an object"
    val reading = if (shape == SourceShapeNames.SHORT_TEXT || shape == SourceShapeNames.LONG_TEXT) {
        fields["text"]
    } else {
        fields["value"]
    }
    return when (shape) {
        SourceShapeNames.NUMERIC_VALUE -> pointProblem(shape, reading, iconPixelSize, warn) ?: sharedNumericProblem(fields)
        SourceShapeNames.ICON, SourceShapeNames.IMAGE -> bitmapProblem(shape, fields, iconPixelSize)
        else -> pointProblem(shape, reading, iconPixelSize, warn)
    }
}

/** One reading: a shape's own field, or one entry of a series' `values`. */
private fun pointProblem(
    shape: String,
    reading: JsonElement?,
    iconPixelSize: IconPixelSize?,
    warn: (String) -> Unit,
): String? {
    val primitive = reading as? JsonPrimitive
    return when (shape) {
        SourceShapeNames.SHORT_TEXT, SourceShapeNames.LONG_TEXT -> {
            if (primitive == null || !primitive.isString) return "$shape needs its text as a string"
            if (shape == SourceShapeNames.SHORT_TEXT && primitive.content.length > SHORT_TEXT_GUIDE_LENGTH) {
                warn("shortText is longer than ~$SHORT_TEXT_GUIDE_LENGTH characters")
            }
            null
        }
        SourceShapeNames.NUMERIC_VALUE ->
            if (primitive == null || primitive.isString || primitive.doubleOrNull?.isFinite() != true) {
                "numericValue needs a number"
            } else {
                null
            }
        SourceShapeNames.TIMESTAMP ->
            if (primitive == null || primitive.isString || primitive.longOrNull == null) {
                "timestamp needs whole epoch seconds"
            } else {
                null
            }
        SourceShapeNames.BOOLEAN ->
            if (primitive == null || primitive.isString || primitive.booleanOrNull == null) "boolean needs true or false" else null
        SourceShapeNames.ICON, SourceShapeNames.IMAGE ->
            (reading as? JsonObject)?.let { bitmapProblem(shape, it, iconPixelSize) } ?: "$shape isn't an object"
        // A shape this build doesn't know: the plugin declared it, so its consumers know what to expect.
        else -> null
    }
}

/** What a numericValue, or a series of them, shares: a unit, and a range that comes as a pair. */
private fun sharedNumericProblem(fields: JsonObject): String? {
    val unit = fields["unit"]
    if (unit != null && (unit !is JsonPrimitive || !unit.isString)) return "numericValue's unit must be a string"
    val min = fields["min"]
    val max = fields["max"]
    if ((min == null) != (max == null)) return "numericValue's min and max come as a pair"
    val bounds = listOfNotNull(min, max)
    if (bounds.any { it !is JsonPrimitive || it.isString || it.doubleOrNull == null }) {
        return "numericValue's min and max must be numbers"
    }
    return null
}

@OptIn(ExperimentalEncodingApi::class)
private fun bitmapProblem(shape: String, fields: JsonObject, iconPixelSize: IconPixelSize?): String? {
    val width = (fields["width"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    val height = (fields["height"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    if (width == null || height == null || width <= 0 || height <= 0) return "$shape needs a positive width and height"
    if (iconPixelSize != null && (width > iconPixelSize.w || height > iconPixelSize.h)) {
        return "$shape is ${width}x$height, larger than the ${iconPixelSize.w}x${iconPixelSize.h} asked for"
    }
    val pixels = fields.base64("pixels") ?: return "$shape's pixels aren't base64"
    val palette = fields.base64("palette") ?: return "$shape's palette isn't base64"
    // 4 bits per pixel, each row padded to a whole byte.
    val expected = (width + 1) / 2 * height
    if (pixels.size.toLong() != expected) return "$shape has ${pixels.size} pixel bytes, not the $expected ${width}x$height needs"
    if (palette.isEmpty() || palette.size > MAX_PALETTE_SIZE) return "$shape's palette has ${palette.size} colours, not 1-$MAX_PALETTE_SIZE"
    return null
}

@OptIn(ExperimentalEncodingApi::class)
private fun JsonObject.base64(field: String): ByteArray? {
    val value = get(field) as? JsonPrimitive ?: return null
    if (!value.isString) return null
    return try {
        Base64.decode(value.content)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun seriesProblem(
    payload: JsonElement,
    declared: Set<String>,
    iconPixelSize: IconPixelSize?,
    warn: (String) -> Unit,
): String? {
    val fields = payload as? JsonObject ?: return "series isn't an object"
    if (declared.isEmpty()) return "series isn't declared"
    val start = fields["start"]
    val step = fields["stepSec"]
    if ((start == null) != (step == null)) return "series' start and stepSec come as a pair"
    if (start != null && pointProblem(SourceShapeNames.TIMESTAMP, start, null, warn) != null) {
        return "series' start needs whole epoch seconds"
    }
    if (step != null && ((step as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: 0) <= 0) {
        return "series' stepSec needs to be a positive whole number"
    }
    val shapes = fields - setOf("start", "stepSec")
    if (shapes.isEmpty()) return "series has no shapes"
    var length: Int? = null
    for ((shape, value) in shapes) {
        if (shape !in declared) return "series.$shape isn't declared"
        val series = value as? JsonObject ?: return "series.$shape isn't an object"
        val values = series["values"] as? JsonArray ?: return "series.$shape needs values"
        if (length != null && values.size != length) return "series' values aren't all the same length"
        length = values.size
        for (point in values) {
            if (point is JsonNull) continue
            pointProblem(shape, point, iconPixelSize, warn)?.let { return "series.$shape: $it" }
        }
        if (shape == SourceShapeNames.NUMERIC_VALUE) sharedNumericProblem(series)?.let { return "series.$shape: $it" }
    }
    return null
}
