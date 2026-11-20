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
public interface Plugin {
    /** Stable identifier for this plugin. Built-ins use the reserved UUID namespace. */
    public val pluginUuid: Uuid

    /** Display name, shown to the user and to an LLM caller. */
    public val name: String

    /** Description of the plugin. */
    public val description: String

    public val sources: List<SourceDeclaration>

    public val actions: List<ActionDeclaration>
        get() = emptyList()

    /** Path of the plugin's script inside its pbw; null for built-in plugins. */
    public val scriptPath: String?
        get() = null

    public val metadata: PluginMetadata
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
     * [limit] is the most instances the subscriber wants (host-clamped to 1..[MAX_LIMIT]); the host
     * drops any past it, or past [MAX_LIMIT] without one. A source with a default cap should let
     * it override that cap.
     *
     * [prompt]: whether the plugin's own permission checks may ask the user.
     */
    public fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        limit: Int?,
        prompt: Boolean,
    ): Flow<SourceUpdate>

    public suspend fun invoke(action: String, args: JsonObject, prompt: Boolean): ActionResult

    public fun serves(category: String, item: String): Boolean = sources.any { it.serves(category, item) }

    public fun action(name: String): ActionDeclaration? = actions.firstOrNull { it.name == name }

    public companion object {
        public const val MAX_LIMIT: Int = 200
    }
}

/** What a [Plugin] offers, without the means to call it. */
public data class PluginMetadata(
    val pluginUuid: Uuid,
    val name: String,
    val description: String,
    val sources: List<SourceDeclaration>,
    val actions: List<ActionDeclaration>,
    val scriptPath: String? = null,
) {
    public fun serves(category: String, item: String): Boolean = sources.any { it.serves(category, item) }

    public fun action(name: String): ActionDeclaration? = actions.firstOrNull { it.name == name }
}

@Serializable
public data class IconPixelSize(val w: Int, val h: Int) {
    /** No bigger than the largest watch screen. */
    public fun clamped(): IconPixelSize = IconPixelSize(w.coerceIn(1, MAX_SIDE), h.coerceIn(1, MAX_SIDE))

    public companion object {
        public const val MAX_SIDE: Int = 260
    }
}

public sealed interface SourceUpdate {
    public data class Data internal constructor(val envelope: SourceEnvelope) : SourceUpdate
    public data class Error(val code: String, val message: String) : SourceUpdate
}

@Serializable
public data class SourceEnvelope(
    val pluginUuid: String,
    val validUntilMs: Long? = null,
    val instances: List<SourceInstance>,
)

@Serializable
public data class SourceInstance(
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
    public companion object {
        public const val PROPERTY_NAME: String = "name"

        /** The Android package an instance's data came from, which per-package permissions are scoped by. */
        public const val PROPERTY_PACKAGE: String = "package"
    }
}

/** Typed shape names. String-based at the wire so the set is extensible. */
public object SourceShapeNames {
    public const val SHORT_TEXT: String = "shortText"
    public const val LONG_TEXT: String = "longText"
    public const val NUMERIC_VALUE: String = "numericValue"
    public const val TIMESTAMP: String = "timestamp"
    public const val BOOLEAN: String = "boolean"
    public const val ICON: String = "icon"
    public const val IMAGE: String = "image"
    public const val SERIES: String = "series"

    /** How a source declares a property whose value is a series of [shape]. */
    public fun series(shape: String): String = "$SERIES.$shape"
}

/**
 * Many readings of one property along an axis. Each shape inside carries `values` in place of its
 * single reading, and keeps whatever else it shares (a numericValue's unit and range); a null is a
 * missing point. [start] (epoch seconds UTC) and [stepSec] come as a pair, for series over time;
 * without them a value's position is all it has.
 */
@Serializable
public data class SeriesShape(
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
public data class NumericSeries(
    val values: List<Double?>,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
)

@Serializable
public data class TextSeries(
    val values: List<String?>,
)

/** Epoch seconds UTC, as [TimestampShape]. */
@Serializable
public data class TimestampSeries(
    val values: List<Long?>,
)

@Serializable
public data class BitmapSeries<T>(
    val values: List<T?>,
)

/**
 * A number, optionally one that fills a gauge. [min] and [max] come as a pair — with them the
 * watch can draw a bar, without them it has a reading to print, which is all a temperature or a
 * share price can honestly offer.
 */
@Serializable
public data class NumericValueShape(
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
public data class TimestampShape(
    val value: Long,
)

@Serializable
public data class ShortTextShape(
    val text: String,
)

@Serializable
public data class LongTextShape(
    val text: String,
)

/**
 * A monochrome glyph, in the same 4-bpp palettised format as [ImageShape] and at the size the
 * subscriber asked for: one ink on a transparent ground, so a consumer can flip the two palette
 * entries to suit its own background. A property with a colour version of the same thing offers
 * it as an [ImageShape] beside this. Only produced when the subscriber sets `iconPixelSize`.
 */
@Serializable
public data class IconShape(
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
public data class ImageShape(
    val pixels: String,
    val palette: String,
    val width: Int,
    val height: Int,
)

/** Just the state: a plugin with words worth showing offers a `shortText` beside this. */
@Serializable
public data class BooleanShape(
    val value: Boolean,
)

/** A native plugin operation that failed with one of the [PluginErrors] codes. */
public class PluginNativeException(
    public val code: String,
    message: String,
) : Exception(message)
