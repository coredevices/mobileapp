package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * A plugin exposes read-only *sources* (subscribed to via `Pebble.subscribeToSource`) and
 * write *actions* (fired via `Pebble.invokeAction`). Built-ins implement this directly and are
 * injected into the Koin `Set<Plugin>`; 3rd-party plugins are backed by JS (see [JsPlugin]).
 * See `new-plugin-api.md`.
 */
interface Plugin {
    /** Stable identifier for this plugin. Built-ins use the reserved UUID namespace. */
    val pluginUuid: Uuid

    /** Display name, shown to the user and to an LLM caller. */
    val name: String

    /** Page to open when the user configures this plugin, already resolved to a loadable URL. */
    val configPageUrl: String?
        get() = null

    val sources: List<SourceDeclaration>

    val actions: List<ActionDeclaration>
        get() = emptyList()

    /**
     * Observe one kind of thing. The returned flow emits whenever the underlying data changes;
     * each emission is a complete envelope (zero or more instances, every property of each).
     *
     * [properties] is what the subscriber actually reads, and is a hint — a plugin may return
     * more, and should only use it to skip work that costs something (a second round trip).
     * Null asks for everything.
     *
     * [iconPixelSize] is the size the subscriber wants `icon`/`image` shapes at. Null means it
     * hasn't asked for them, and a plugin must not do the work of producing them: text and
     * numbers are free once fetched, bitmaps are not.
     */
    fun observe(
        category: String,
        item: String,
        properties: List<String>? = null,
        iconPixelSize: IconPixelSize? = null,
    ): Flow<SourceEnvelope>

    suspend fun invoke(action: String, args: JsonObject): ActionResult =
        ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "$name exposes no actions")

    fun serves(category: String, item: String) = sources.any { it.serves(category, item) }

    fun action(name: String) = actions.firstOrNull { it.name == name }
}

@Serializable
data class IconPixelSize(val w: Int, val h: Int)

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
}

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
