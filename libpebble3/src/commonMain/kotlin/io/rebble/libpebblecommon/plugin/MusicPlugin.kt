package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.SystemMusicControl
import io.rebble.libpebblecommon.music.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * Built-in plugin for whatever is playing on the phone. Android only — registered from the
 * Android platform module, because [SystemMusicControl] is the only thing that can see another
 * app's media session there, and iOS has no equivalent.
 */
class MusicPlugin(
    private val music: SystemMusicControl,
) : Plugin {
    override val pluginUuid: Uuid = BUILT_IN_MUSIC_UUID
    override val name: String = "Music"

    private val logger = Logger.withTag("MusicPlugin")

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_TRACK),
            properties = mapOf(
                PROPERTY_TITLE to TEXT_SHAPES,
                PROPERTY_ARTIST to TEXT_SHAPES,
                PROPERTY_ALBUM to TEXT_SHAPES,
                PROPERTY_PLAYING to listOf(
                    SourceShapeNames.BOOLEAN,
                    SourceShapeNames.SHORT_TEXT,
                ),
                PROPERTY_ARTWORK to listOf(SourceShapeNames.IMAGE),
            ),
            supportsMultiple = false,
            suggestedRefreshIntervalSec = 5,
        ),
    )

    override val actions: List<ActionDeclaration> = listOf(
        ActionDeclaration(
            name = ACTION_SET_PLAYING,
            description = "Resume or pause playback.",
            parameters = schema("""{"on":{"type":"boolean"}}""", required = listOf("on")),
            targets = listOf("$CATEGORY/$ITEM_TRACK/$PROPERTY_PLAYING"),
        ),
        ActionDeclaration(
            name = ACTION_NEXT,
            description = "Skip to the next track.",
            parameters = schema("{}"),
            targets = listOf(TRACK),
        ),
        ActionDeclaration(
            name = ACTION_PREVIOUS,
            description = "Go back to the previous track.",
            parameters = schema("{}"),
            targets = listOf(TRACK),
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        val artworkSize = iconPixelSize
            ?.takeIf { properties == null || PROPERTY_ARTWORK in properties }
        if (artworkSize == null) {
            // Nobody wants artwork, so nothing here should ever encode any.
            return music.playbackState.map { status -> toEnvelope(status, null) }
        }
        // Artwork often arrives after the track it belongs to, so re-read on both.
        return combine(
            music.playbackState,
            music.albumArtUpdated.onStart { emit(Unit) },
        ) { status, _ -> status }
            .map { status -> toEnvelope(status, artworkSize) }
    }

    override suspend fun invoke(action: String, args: JsonObject): ActionResult = when (action) {
        ACTION_SET_PLAYING -> {
            val on = args["on"]?.toString() == "true"
            if (on) music.play() else music.pause()
            ActionResult(ok = true, text = if (on) "Playing." else "Paused.", refreshed = REFRESHED)
        }
        ACTION_NEXT -> {
            music.nextTrack()
            ActionResult(ok = true, text = "Skipped.", refreshed = REFRESHED)
        }
        ACTION_PREVIOUS -> {
            music.previousTrack()
            ActionResult(ok = true, text = "Back a track.", refreshed = REFRESHED)
        }
        else -> ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "no action $action")
    }

    private suspend fun toEnvelope(
        status: PlaybackStatus?,
        iconPixelSize: IconPixelSize?,
    ): SourceEnvelope {
        val track = status?.currentTrack
        val instances = if (track == null) emptyList() else listOf(
            SourceInstance(
                instanceId = INSTANCE_ID,
                properties = buildMap {
                    put(PROPERTY_TITLE, textShapes(track.title))
                    put(PROPERTY_ARTIST, textShapes(track.artist))
                    put(PROPERTY_ALBUM, textShapes(track.album))
                    val playing = status.playbackState.isActive()
                    put(
                        PROPERTY_PLAYING,
                        mapOf(
                            SourceShapeNames.BOOLEAN to encode(BooleanShape(playing)),
                            SourceShapeNames.SHORT_TEXT to encode(
                                ShortTextShape(if (playing) "Playing" else "Paused")
                            ),
                        ),
                    )
                    iconPixelSize
                        ?.let { artwork(track.title, track.artist, it) }
                        ?.let { put(PROPERTY_ARTWORK, mapOf(SourceShapeNames.IMAGE to it)) }
                },
            )
        )
        return SourceEnvelope(
            pluginUuid = pluginUuid.toString(),
            validUntilMs = null,
            instances = instances,
        )
    }

    private fun textShapes(reading: String?) = mapOf(
        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(reading ?: "?")),
        SourceShapeNames.LONG_TEXT to encode(LongTextShape(reading ?: "?")),
    )

    /**
     * Encoding a bitmap is the expensive part of an emission, and playback state changes far more
     * often than the track does — so the last one is kept until the track or the size changes.
     */
    private var cached: Pair<String, JsonElement>? = null

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun artwork(
        title: String?,
        artist: String?,
        size: IconPixelSize,
    ): JsonElement? {
        if (!music.supportsAlbumArt || title == null) return null
        val key = "$title|$artist|${size.w}x${size.h}"
        cached?.let { (cachedKey, art) -> if (cachedKey == key) return art }
        val art = try {
            music.getAlbumArt(title, artist.orEmpty(), size.w, size.h)
        } catch (e: Exception) {
            logger.w(e) { "album art failed" }
            null
        } ?: return null
        val shape = encode(
            ImageShape(
                pixels = Base64.encode(art.pixels.toByteArray()),
                palette = Base64.encode(art.palette.toByteArray()),
                width = art.width,
                height = art.height,
            )
        )
        cached = key to shape
        return shape
    }

    private fun schema(properties: String, required: List<String> = emptyList()): JsonObject {
        val requiredJson = required.joinToString(",") { "\"$it\"" }
        return Json.decodeFromString(
            JsonObject.serializer(),
            """{"type":"object","properties":$properties,"required":[$requiredJson]}""",
        )
    }

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "music"
        const val ITEM_TRACK = "track"
        const val PROPERTY_TITLE = "title"
        const val PROPERTY_ARTIST = "artist"
        const val PROPERTY_ALBUM = "album"
        const val PROPERTY_PLAYING = "playing"
        const val PROPERTY_ARTWORK = "artwork"
        const val ACTION_SET_PLAYING = "set_playing"
        const val ACTION_NEXT = "next_track"
        const val ACTION_PREVIOUS = "previous_track"

        private const val TRACK = "$CATEGORY/$ITEM_TRACK"
        private const val INSTANCE_ID = "track"
        private val REFRESHED = listOf(TRACK)
        private val TEXT_SHAPES =
            listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.LONG_TEXT)

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_MUSIC_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000003")
    }
}
