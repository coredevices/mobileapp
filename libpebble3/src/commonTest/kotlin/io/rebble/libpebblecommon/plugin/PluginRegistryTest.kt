package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.imaging.EncodedImage
import io.rebble.libpebblecommon.music.PlaybackState
import io.rebble.libpebblecommon.music.PlaybackStatus
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.music.SystemMusicControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private class FakePlugin(
    override val pluginUuid: Uuid,
    override val name: String,
    override val sources: List<SourceDeclaration> = emptyList(),
    override val actions: List<ActionDeclaration> = emptyList(),
) : Plugin {
    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> =
        flowOf(SourceEnvelope(pluginUuid.toString(), null, emptyList()))
}

private object FakeMusicControl : SystemMusicControl {
    override fun play() = Unit
    override fun pause() = Unit
    override fun playPause() = Unit
    override fun nextTrack() = Unit
    override fun previousTrack() = Unit
    override fun volumeDown() = Unit
    override fun volumeUp() = Unit
    override val playbackState = MutableStateFlow<PlaybackStatus?>(null)
    override val supportsAlbumArt = false
    override suspend fun getAlbumArt(title: String, artist: String, width: Int, height: Int) = null
    override val albumArtUpdated = emptyFlow<Unit>()
}

private val UUID_A = Uuid.parse("00000000-0000-0000-0002-00000000000a")
private val UUID_B = Uuid.parse("00000000-0000-0000-0002-00000000000b")

private fun weatherSource() = SourceDeclaration(
    category = "weather",
    items = listOf("location"),
    properties = mapOf(
        "temperature" to listOf(SourceShapeNames.SHORT_TEXT),
        "condition" to listOf(SourceShapeNames.SHORT_TEXT),
    ),
)

private fun enabled(enabled: Boolean = true) = WatchConfig(enablePlugins = enabled).asFlow()

class PluginRegistryTest {

    @Test
    fun findsSourcePluginByCategoryAndItem() {
        val plugin = FakePlugin(UUID_A, "A", listOf(weatherSource()))
        val registry = PluginRegistry(setOf(plugin), enabled())

        assertEquals(plugin, registry.findSourcePlugin("weather", "location"))
        assertNull(registry.findSourcePlugin("weather", "forecast"))
        assertNull(registry.findSourcePlugin("home", "location"))
    }

    @Test
    fun prefersRequestedPluginButFallsBack() {
        val a = FakePlugin(UUID_A, "A", listOf(weatherSource()))
        val b = FakePlugin(UUID_B, "B", listOf(weatherSource()))
        val registry = PluginRegistry(setOf(a, b), enabled())

        assertEquals(b, registry.findSourcePlugin("weather", "location", UUID_B.toString()))
        // An unknown preference must not fail the lookup — sources are fungible.
        assertEquals(
            true,
            registry.findSourcePlugin("weather", "location", "not-a-plugin") in listOf(a, b),
        )
    }

    @Test
    fun actionLookupIsExactAndNeverFallsBack() {
        val a = FakePlugin(UUID_A, "A", listOf(weatherSource()))
        val registry = PluginRegistry(setOf(a), enabled())

        assertEquals(a, registry.findPlugin(UUID_A.toString()))
        assertNull(registry.findPlugin(UUID_B.toString()))
    }

    @Test
    fun registerJsPluginsAtRuntime() {
        val registry = PluginRegistry(emptySet(), enabled())
        assertNull(registry.findSourcePlugin("weather", "location"))

        val plugin = FakePlugin(UUID_A, "A", listOf(weatherSource()))
        registry.registerPlugin(plugin)
        assertEquals(plugin, registry.findSourcePlugin("weather", "location"))
        assertEquals(1, registry.all().size)
    }

    @Test
    fun nothingIsVisibleWhilePluginsAreDisabled() {
        val plugin = FakePlugin(UUID_A, "A", listOf(weatherSource()))
        val registry = PluginRegistry(setOf(plugin), enabled(false))

        assertTrue(registry.all().isEmpty())
        assertNull(registry.findPlugin(UUID_A.toString()))
        assertNull(registry.findSourcePlugin("weather", "location"))
    }
}

class ActionDeclarationTest {

    private fun schema(json: String) = Json.decodeFromString(JsonObject.serializer(), json)

    @Test
    fun requiredParamsReadFromSchema() {
        val action = ActionDeclaration(
            name = "set_brightness",
            parameters = schema(
                """{"type":"object","properties":{"instanceId":{"type":"string"},
                   "percent":{"type":"integer"}},"required":["instanceId","percent"]}"""
            ),
        )
        assertEquals(listOf("instanceId", "percent"), action.requiredParams)
    }

    @Test
    fun bindableWhenOnlyInstanceIdIsRequired() {
        val toggle = ActionDeclaration(
            name = "toggle",
            parameters = schema(
                """{"type":"object","properties":{"instanceId":{"type":"string"}},
                   "required":["instanceId"]}"""
            ),
        )
        assertTrue(toggle.bindable)
    }

    /** An action across several items needs to know which one, and the tile knows that too. */
    @Test
    fun bindableWhenOnlyTheTileFillsEveryRequiredParam() {
        val setOn = ActionDeclaration(
            name = "set_on",
            parameters = schema(
                """{"type":"object","properties":{"item":{"type":"string"},
                   "instanceId":{"type":"string"}},"required":["item","instanceId"]}"""
            ),
        )
        assertTrue(setOn.bindable)
    }

    @Test
    fun bindableWhenNothingIsRequired() {
        val pair = ActionDeclaration(
            name = "pair",
            parameters = schema("""{"type":"object","properties":{}}"""),
        )
        assertTrue(pair.bindable)
        assertTrue(ActionDeclaration(name = "no-schema").bindable)
    }

    @Test
    fun notBindableWhenAnotherParamIsRequired() {
        val setBrightness = ActionDeclaration(
            name = "set_brightness",
            parameters = schema(
                """{"type":"object","properties":{"instanceId":{"type":"string"},
                   "percent":{"type":"integer"}},"required":["instanceId","percent"]}"""
            ),
        )
        assertFalse(setBrightness.bindable)
    }
}

class SourceDeclarationTest {

    @Test
    fun servesMatchesCategoryAndItem() {
        val source = weatherSource()
        assertTrue(source.serves("weather", "location"))
        // Properties are not items: a subscription addresses the thing, not one reading of it.
        assertFalse(source.serves("weather", "temperature"))
        assertFalse(source.serves("home", "location"))
    }

    @Test
    fun everyPropertyDeclaresItsOwnShapes() {
        val source = weatherSource()
        assertEquals(listOf("temperature", "condition"), source.properties.keys.toList())
        assertEquals(listOf(SourceShapeNames.SHORT_TEXT), source.properties["temperature"])
    }
}

class ShapeTest {

    private val json = Json { encodeDefaults = true }

    /** Without a range it is just a number and its unit — a temperature has no gauge to fill. */
    @Test
    fun aNumericValueNeedsNoRange() {
        val encoded = json.encodeToString(
            NumericValueShape.serializer(),
            NumericValueShape(value = 20.0, unit = "°C"),
        )
        assertEquals("""{"value":20.0,"unit":"°C","min":null,"max":null}""", encoded)
    }

    /** However it was written, a consumer reading the catalogue sees one shape. */
    @Test
    fun aPermissionAlwaysReadsBackAsAnObject() {
        val encoded = Json.encodeToString(
            ListSerializer(PluginPermission.serializer()),
            listOf(
                PluginPermission("LocalNetwork"),
                PluginPermission("Internet", mapOf("domains" to listOf("example.com"))),
            ),
        )
        assertEquals(
            """[{"name":"LocalNetwork"},""" +
                """{"name":"Internet","parameters":{"domains":["example.com"]}}]""",
            encoded,
        )
    }

    @Test
    fun aTimestampIsEpochSeconds() {
        val encoded = json.encodeToString(
            TimestampShape.serializer(),
            TimestampShape(1716940000L),
        )
        assertEquals("""{"value":1716940000}""", encoded)
    }
}

class PluginManifestTest {

    @Test
    fun parsesTheBundledManifestShape() {
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString(
            PluginManifest.serializer(),
            """
            {
              "uuid": "6f9c1a44-3d1e-4b8a-9c2f-0d5e7a1b3c40",
              "name": "Hue",
              "description": "lights",
              "script": "plugin.js",
              "sources": [
                {
                  "category": "home",
                  "items": ["room", "light"],
                  "properties": {
                    "name": ["shortText"],
                    "on": ["boolean", "shortText"]
                  },
                  "supportsMultiple": true,
                  "usesPermissions": [
                    "LocalNetwork",
                    {"name": "Internet", "parameters": {"domains": ["discovery.meethue.com"]}}
                  ],
                  "callerPermissions": ["HomeControl"],
                  "suggestedRefreshIntervalSec": 30
                }
              ],
              "actions": [
                {
                  "name": "set_on",
                  "description": "Turn a light or a room on or off.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "item": {"type": "string"},
                      "instanceId": {"type": "string"},
                      "on": {"type": "boolean"}
                    },
                    "required": ["item", "instanceId", "on"]
                  },
                  "targets": ["home/room/on", "home/light/on"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Hue", manifest.name)
        assertEquals(1, manifest.sources.size)
        assertEquals(30, manifest.sources[0].suggestedRefreshIntervalSec)
        assertTrue(manifest.sources[0].supportsMultiple)
        assertEquals(listOf("room", "light"), manifest.sources[0].items)
        // What the plugin needs of the user, and what it needs of whoever reads it. A bare name
        // and a name with arguments are both accepted.
        assertEquals(
            listOf(
                PluginPermission("LocalNetwork"),
                PluginPermission(
                    "Internet",
                    mapOf("domains" to listOf("discovery.meethue.com")),
                ),
            ),
            manifest.sources[0].usesPermissions,
        )
        assertEquals(
            listOf(PluginPermission("HomeControl")),
            manifest.sources[0].callerPermissions,
        )
        assertEquals(
            listOf(SourceShapeNames.BOOLEAN, SourceShapeNames.SHORT_TEXT),
            manifest.sources[0].properties["on"],
        )
        assertEquals(listOf("home/room/on", "home/light/on"), manifest.actions[0].targets)
        assertFalse(manifest.actions[0].destructive)
    }

    @Test
    fun aSourceNeedsNoPermissionsAtAll() {
        val manifest = Json.decodeFromString(
            PluginManifest.serializer(),
            """{"uuid":"$UUID_A","name":"Stocks","sources":[{"category":"finance",
               "items":["stock"],"properties":{"price":["shortText"]}}]}""",
        )
        assertTrue(manifest.sources[0].usesPermissions.isEmpty())
        assertTrue(manifest.sources[0].callerPermissions.isEmpty())
    }

    @Test
    fun defaultsFillInForAMinimalManifest() {
        val manifest = Json.decodeFromString(
            PluginManifest.serializer(),
            """{"uuid":"$UUID_A","name":"Minimal"}""",
        )
        assertEquals("plugin.js", manifest.script)
        assertTrue(manifest.sources.isEmpty())
        assertTrue(manifest.actions.isEmpty())
    }
}

class MusicPluginTest {

    private val declaration = MusicPlugin(FakeMusicControl).sources.single()

    /** Title, artist and album are facets of one track: one item, one instance, one fetch. */
    @Test
    fun theTrackIsOneThingWithEveryFacetAsAProperty() {
        assertTrue(declaration.serves(MusicPlugin.CATEGORY, MusicPlugin.ITEM_TRACK))
        assertEquals(listOf(MusicPlugin.ITEM_TRACK), declaration.items)
        assertEquals(
            listOf(
                MusicPlugin.PROPERTY_TITLE,
                MusicPlugin.PROPERTY_ARTIST,
                MusicPlugin.PROPERTY_ALBUM,
                MusicPlugin.PROPERTY_PLAYING,
                MusicPlugin.PROPERTY_ARTWORK,
            ),
            declaration.properties.keys.toList(),
        )
        assertEquals(
            listOf(SourceShapeNames.IMAGE),
            declaration.properties[MusicPlugin.PROPERTY_ARTWORK],
        )
    }

    /** Play/pause writes one reading; skipping applies to the track however it is shown. */
    @Test
    fun actionsTargetWhatTheyWrite() {
        val actions = MusicPlugin(FakeMusicControl).actions
        assertEquals(
            listOf("${MusicPlugin.CATEGORY}/${MusicPlugin.ITEM_TRACK}/${MusicPlugin.PROPERTY_PLAYING}"),
            actions.first().targets,
        )
        assertTrue(
            actions.drop(1).all {
                it.targets == listOf("${MusicPlugin.CATEGORY}/${MusicPlugin.ITEM_TRACK}")
            }
        )
    }

    /** A tap can fill `on` from the boolean shape; skip/previous need nothing at all. */
    @Test
    fun everyActionIsBindableFromWhatIsOnScreen() {
        val actions = MusicPlugin(FakeMusicControl).actions
        assertEquals(
            listOf(MusicPlugin.ACTION_SET_PLAYING, MusicPlugin.ACTION_NEXT, MusicPlugin.ACTION_PREVIOUS),
            actions.map { it.name },
        )
        assertEquals(listOf("on"), actions.first().requiredParams)
        assertTrue(actions.drop(1).all { it.requiredParams.isEmpty() })
        assertTrue(actions.drop(1).all { it.bindable })
    }
}

class MusicArtworkTest {

    private class CountingMusic : SystemMusicControl by FakeMusicControl {
        var artRequests = 0
        override val playbackState = MutableStateFlow(
            PlaybackStatus(
                playerInfo = null,
                playbackState = PlaybackState.Playing,
                currentTrack = MusicTrack("Song", "Artist", "Album", Duration.ZERO),
                playbackPositionMs = 0,
                playbackRate = 1f,
                shuffle = false,
                repeat = RepeatType.Off,
                volume = 50,
            )
        )
        override val supportsAlbumArt = true
        override suspend fun getAlbumArt(
            title: String,
            artist: String,
            width: Int,
            height: Int,
        ): EncodedImage? {
            artRequests++
            return EncodedImage(width, height, ubyteArrayOf(0u), ubyteArrayOf(0u))
        }
    }

    @Test
    fun noArtworkIsEncodedWhenNoneWasAskedFor() = runTest {
        val music = CountingMusic()
        val envelope = MusicPlugin(music)
            .observe(MusicPlugin.CATEGORY, MusicPlugin.ITEM_TRACK, iconPixelSize = null)
            .first()

        assertEquals(0, music.artRequests)
        assertNull(envelope.instances.single().properties[MusicPlugin.PROPERTY_ARTWORK])
    }

    /** The subscriber said which properties it reads, and artwork wasn't one of them. */
    @Test
    fun noArtworkIsEncodedWhenTheSubscriberOnlyReadsOtherProperties() = runTest {
        val music = CountingMusic()
        val envelope = MusicPlugin(music).observe(
            MusicPlugin.CATEGORY,
            MusicPlugin.ITEM_TRACK,
            properties = listOf(MusicPlugin.PROPERTY_TITLE),
            iconPixelSize = IconPixelSize(24, 24),
        ).first()

        assertEquals(0, music.artRequests)
        assertNull(envelope.instances.single().properties[MusicPlugin.PROPERTY_ARTWORK])
    }

    @Test
    fun artworkIsEncodedAtTheRequestedSizeAndReusedUntilTheTrackChanges() = runTest {
        val music = CountingMusic()
        val plugin = MusicPlugin(music)
        val size = IconPixelSize(24, 24)

        val image = plugin
            .observe(MusicPlugin.CATEGORY, MusicPlugin.ITEM_TRACK, iconPixelSize = size).first()
            .instances.single()
            .properties.getValue(MusicPlugin.PROPERTY_ARTWORK)
            .getValue(SourceShapeNames.IMAGE)
        assertEquals(24, Json.decodeFromJsonElement(ImageShape.serializer(), image).width)
        assertEquals(1, music.artRequests)

        // Playback state churns constantly; that must not cause a re-encode.
        plugin.observe(MusicPlugin.CATEGORY, MusicPlugin.ITEM_TRACK, iconPixelSize = size).first()
        assertEquals(1, music.artRequests)
    }
}
