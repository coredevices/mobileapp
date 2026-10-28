package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * A plugin exposes read-only *sources* (subscribed to via `Pebble.subscribeToSource`) and
 * write *actions* (fired via `Pebble.invokeAction`). Built-ins implement [NativePlugin]; 3rd-party
 * plugins are backed by JS (see [JsPlugin]). libpebble only hands out [metadata]; calls go
 * through `Plugins`. See `new-plugin-api.md`.
 */
interface Plugin {
    /** Stable identifier for this plugin. Built-ins use the reserved UUID namespace. */
    val pluginUuid: Uuid

    /** Display name, shown to the user and to an LLM caller. */
    val name: String

    /** Description of the plugin. */
    val description: String

    val sources: List<SourceDeclaration>

    val actions: List<ActionDeclaration>
        get() = emptyList()

    /** Path of the plugin's script inside its pbw; null for built-in plugins. */
    val scriptPath: String?
        get() = null

    val metadata: PluginMetadata
        get() = PluginMetadata(pluginUuid, name, description, sources, actions, scriptPath)

    /**
     * Observe one kind of thing. The returned flow emits whenever the underlying data changes;
     * each emission is a complete envelope (zero or more instances, every property of each), or
     * an error that doesn't end the flow.
     *
     * [properties] is what the subscriber actually reads, and is a hint — a plugin may return
     * more, and should only use it to skip work that costs something (a second round trip).
     * Null asks for everything.
     *
     * [iconPixelSize] is the size the subscriber wants `icon`/`image` shapes at. Null means it
     * hasn't asked for them, and a plugin must not do the work of producing them: text and
     * numbers are free once fetched, bitmaps are not.
     *
     * [prompt]: whether the plugin's own permission checks may ask the user.
     */
    fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        prompt: Boolean,
    ): Flow<SourceUpdate>

    suspend fun invoke(action: String, args: JsonObject, prompt: Boolean): ActionResult

    fun serves(category: String, item: String) = sources.any { it.serves(category, item) }

    fun action(name: String) = actions.firstOrNull { it.name == name }
}

/** What a [Plugin] offers, without the means to call it. */
data class PluginMetadata(
    val pluginUuid: Uuid,
    val name: String,
    val description: String,
    val sources: List<SourceDeclaration>,
    val actions: List<ActionDeclaration>,
    val scriptPath: String? = null,
) {
    fun serves(category: String, item: String) = sources.any { it.serves(category, item) }

    fun action(name: String) = actions.firstOrNull { it.name == name }
}

@Serializable
data class IconPixelSize(val w: Int, val h: Int)

sealed interface SourceUpdate {
    data class Data(val envelope: SourceEnvelope) : SourceUpdate
    data class Error(val code: String, val message: String) : SourceUpdate
}

@Serializable
data class SourceEnvelope(
    val pluginUuid: String,
    val validUntilMs: Long? = null,
    val instances: List<SourceInstance>,
)

@Serializable
data class SourceInstance(
    /** Stable, plugin-defined identifier (used by user-preference pinning). */
    val instanceId: String,
    /**
     * Property name -> shape name (`numericValue`, `shortText`, `boolean`, `icon`, `image`,
     * `longText`) -> payload as a JsonElement. Plugins serialize known shape payload types; the
     * JS-side consumer reads the property and shape it wants by name.
     *
     * [PROPERTY_NAME] is the one reserved property: it is what a picker shows for this instance.
     */
    val properties: Map<String, Map<String, JsonElement>>,
) {
    companion object {
        const val PROPERTY_NAME = "name"

        /** The Android package an instance's data came from, which per-package permissions are scoped by. */
        const val PROPERTY_PACKAGE = "package"
    }
}

/** Typed shape names. String-based at the wire so the set is extensible. */
object SourceShapeNames {
    const val SHORT_TEXT = "shortText"
    const val LONG_TEXT = "longText"
    const val NUMERIC_VALUE = "numericValue"
    const val TIMESTAMP = "timestamp"
    const val BOOLEAN = "boolean"
    const val ICON = "icon"
    const val IMAGE = "image"
    const val SERIES = "series"

    /** How a source declares a property whose value is a series of [shape]. */
    fun series(shape: String) = "$SERIES.$shape"
}

/**
 * Many readings of one property along an axis. Each shape inside carries `values` in place of its
 * single reading, and keeps whatever else it shares (a numericValue's unit and range); a null is a
 * missing point. [start] (epoch seconds UTC) and [stepSec] come as a pair, for series over time;
 * without them a value's position is all it has.
 */
@Serializable
data class SeriesShape(
    val start: Long? = null,
    val stepSec: Long? = null,
    val numericValue: NumericSeries? = null,
    val shortText: TextSeries? = null,
    val longText: TextSeries? = null,
    val icon: BitmapSeries<IconShape>? = null,
    val image: BitmapSeries<ImageShape>? = null,
    val timestamp: TimestampSeries? = null,
)

@Serializable
data class NumericSeries(
    val values: List<Double?>,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
)

@Serializable
data class TextSeries(
    val values: List<String?>,
)

/** Epoch seconds UTC, as [TimestampShape]. */
@Serializable
data class TimestampSeries(
    val values: List<Long?>,
)

@Serializable
data class BitmapSeries<T>(
    val values: List<T?>,
)

/**
 * A number, optionally one that fills a gauge. [min] and [max] come as a pair — with them the
 * watch can draw a bar, without them it has a reading to print, which is all a temperature or a
 * share price can honestly offer.
 */
@Serializable
data class NumericValueShape(
    val value: Double,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
)

/**
 * A moment in time as epoch seconds UTC, left for the watch to render — it knows the user's
 * clock format, and it is the only side that can keep "in 5 minutes" honest as time passes.
 */
@Serializable
data class TimestampShape(
    val value: Long,
)

@Serializable
data class ShortTextShape(
    val text: String,
)

@Serializable
data class LongTextShape(
    val text: String,
)

/**
 * A monochrome glyph, in the same 4-bpp palettised format as [ImageShape] and at the size the
 * subscriber asked for: one ink on a transparent ground, so a consumer can flip the two palette
 * entries to suit its own background. A property with a colour version of the same thing offers
 * it as an [ImageShape] beside this. Only produced when the subscriber sets `iconPixelSize`.
 */
@Serializable
data class IconShape(
    val pixels: String,
    val palette: String,
    val width: Int,
    val height: Int,
)

/**
 * A full-colour bitmap in the watch's own format: 4-bpp palettised, both parts base64'd.
 * [pixels] packs two indices per byte with rows padded to `ceil(width / 2)`; [palette] is up to
 * 16 GColor8 bytes.
 *
 * Sized to the `iconPixelSize` the subscriber asked for.
 */
@Serializable
data class ImageShape(
    val pixels: String,
    val palette: String,
    val width: Int,
    val height: Int,
)

/** Just the state: a plugin with words worth showing offers a `shortText` beside this. */
@Serializable
data class BooleanShape(
    val value: Boolean,
)

/** A native plugin operation that failed with one of the [PluginErrors] codes. */
class PluginNativeException(
    val code: String,
    message: String,
) : Exception(message)
