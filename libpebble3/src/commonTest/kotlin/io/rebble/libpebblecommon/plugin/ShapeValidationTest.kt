package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val DECLARATION = SourceDeclaration(
    category = "home",
    items = listOf("light"),
    properties = mapOf(
        "name" to listOf(SourceShapeNames.SHORT_TEXT),
        "on" to listOf(SourceShapeNames.BOOLEAN, SourceShapeNames.SHORT_TEXT),
        "brightness" to listOf(SourceShapeNames.NUMERIC_VALUE),
        "changed_at" to listOf(SourceShapeNames.TIMESTAMP),
        "glyph" to listOf(SourceShapeNames.ICON),
        "history" to listOf(
            SourceShapeNames.series(SourceShapeNames.NUMERIC_VALUE),
            SourceShapeNames.series(SourceShapeNames.SHORT_TEXT),
        ),
    ),
)

@OptIn(ExperimentalEncodingApi::class)
private fun bitmap(width: Int, height: Int, pixelBytes: Int = (width + 1) / 2 * height) =
    """{"pixels":"${Base64.encode(ByteArray(pixelBytes))}","palette":"${Base64.encode(ByteArray(2))}","width":$width,"height":$height}"""

class ShapeValidationTest {
    private val reports = mutableListOf<String>()

    /** The shapes left of each property, after validating one instance holding [properties]. */
    private fun kept(properties: String, iconPixelSize: IconPixelSize? = null): Map<String, Set<String>> {
        val parsed = Json.parseToJsonElement(properties).jsonObject
            .mapValues { (_, shapes) -> shapes.jsonObject.toMap<String, JsonElement>() }
        val envelope = SourceEnvelope("p", null, listOf(SourceInstance("1", parsed)))
        return envelope.validated(DECLARATION, iconPixelSize) { property, problem -> reports += "$property: $problem" }
            .instances.single().properties.mapValues { it.value.keys }
    }

    @Test
    fun wellFormedShapesAllSurvive() {
        val properties = """{
            "name": {"shortText": {"text": "Hall"}},
            "on": {"boolean": {"value": true}, "shortText": {"text": "On"}},
            "brightness": {"numericValue": {"value": 64, "unit": "%", "min": 0, "max": 100}},
            "changed_at": {"timestamp": {"value": 1729468800}},
            "glyph": {"icon": ${bitmap(5, 4)}},
            "history": {"series": {"start": 1729468800, "stepSec": 3600,
                "numericValue": {"values": [1, null, 3], "unit": "%"},
                "shortText": {"values": ["1%", null, "3%"]}}}
        }"""

        assertEquals(DECLARATION.properties.keys, kept(properties).keys)
        assertEquals(emptyList(), reports)
    }

    @Test
    fun whatTheSourceDoesntDeclareIsDropped() {
        val kept = kept("""{
            "colour": {"shortText": {"text": "Red"}},
            "on": {"boolean": {"value": true}, "longText": {"text": "Switched on"}}
        }""")

        assertEquals(mapOf("on" to setOf(SourceShapeNames.BOOLEAN)), kept)
    }

    @Test
    fun aPayloadOfTheWrongTypeIsDropped() {
        val kept = kept("""{
            "name": {"shortText": "Hall"},
            "on": {"boolean": {"value": "true"}, "shortText": {"text": "On"}},
            "brightness": {"numericValue": {"value": "64"}},
            "changed_at": {"timestamp": {"value": 1729468800.5}}
        }""")

        assertEquals(mapOf("on" to setOf(SourceShapeNames.SHORT_TEXT)), kept)
        assertEquals(4, reports.size)
    }

    @Test
    fun aRangeComesAsAPair() {
        assertEquals(emptyMap(), kept("""{"brightness": {"numericValue": {"value": 64, "max": 100}}}"""))
    }

    @Test
    fun aReadingOutsideItsRangeIsKept() {
        // A UV index of 12 on a 0-11 scale is still "extreme", not a mistake.
        assertEquals(1, kept("""{"brightness": {"numericValue": {"value": 12, "min": 0, "max": 11}}}""").size)
    }

    @Test
    fun aBitmapMustMatchItsSizeAndFitWhatWasAskedFor() {
        assertEquals(emptyMap(), kept("""{"glyph": {"icon": ${bitmap(5, 4, pixelBytes = 10)}}}"""))
        assertEquals(emptyMap(), kept("""{"glyph": {"icon": ${bitmap(32, 32)}}}""", IconPixelSize(26, 26)))
        assertEquals(1, kept("""{"glyph": {"icon": ${bitmap(26, 26)}}}""", IconPixelSize(26, 26)).size)
    }

    @Test
    fun aSeriesMustBeConsistent() {
        assertEquals(emptyMap(), kept("""{"history": {"series": {
            "numericValue": {"values": [1, 2, 3]}, "shortText": {"values": ["1", "2"]}}}}"""))
        assertEquals(emptyMap(), kept("""{"history": {"series": {"start": 1729468800,
            "numericValue": {"values": [1]}}}}"""))
        assertEquals(emptyMap(), kept("""{"history": {"series": {"longText": {"values": ["a"]}}}}"""))
        assertEquals(emptyMap(), kept("""{"history": {"series": {"numericValue": {"values": [1, "2"]}}}}"""))
    }

    @Test
    fun aSeriesNeedsDeclaringAsOne() {
        assertEquals(emptyMap(), kept("""{"brightness": {"series": {"numericValue": {"values": [1]}}}}"""))
    }

    @Test
    fun aLongShortTextIsKeptButReported() {
        assertEquals(1, kept("""{"name": {"shortText": {"text": "Living room lamp"}}}""").size)
        assertTrue(reports.single().contains("longer than"))
    }
}
