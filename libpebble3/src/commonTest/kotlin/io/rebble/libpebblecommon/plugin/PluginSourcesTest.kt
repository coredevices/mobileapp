package io.rebble.libpebblecommon.plugin

import com.russhwolf.settings.MapSettings
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.permissions.PermissionSubject
import io.rebble.libpebblecommon.permissions.notificationsKey
import io.rebble.libpebblecommon.plugin.PluginPermissions.NOTIFICATIONS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

private val READER = Uuid.parse("00000000-0000-0000-0002-0000000000a1")
private val BUILT_IN = Uuid.parse("00000000-0000-0000-0001-0000000000a2")
private val OTHER = Uuid.parse("00000000-0000-0000-0002-0000000000a3")

private fun source(category: String, item: String, vararg callerPermissions: String) = SourceDeclaration(
    category = category,
    items = listOf(item),
    callerPermissions = callerPermissions.map { PluginPermission(it) },
)

private class BuiltIn(override val sources: List<SourceDeclaration>) : NativePlugin {
    override val pluginUuid = BUILT_IN
    override val name = "Preferences"
    override val description = ""
    override fun observe(category: String, item: String, properties: List<String>?, iconPixelSize: IconPixelSize?) =
        emptyFlow<SourceEnvelope>()
}

private class Js(override val pluginUuid: Uuid, override val name: String, override val sources: List<SourceDeclaration>) : Plugin {
    override val description = ""
    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        prompt: Boolean,
    ): Flow<SourceUpdate> = emptyFlow()

    override suspend fun invoke(action: String, args: JsonObject, prompt: Boolean) =
        ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "none")
}

private fun served(plugin: Uuid) = SourceUpdate.Data(SourceEnvelope(plugin.toString(), null, emptyList()))

class PluginSourcesTest {
    private val readAs = mutableListOf<Pair<PluginCaller, Uuid>>()
    /** What the reader declared, per permission; otherwise the bare permission. */
    private val declared = mutableMapOf<String, List<String>>()

    private fun sources(
        vararg plugins: Plugin,
        upstream: (PluginMetadata) -> Flow<SourceUpdate> = { flowOf(served(it.pluginUuid)) },
    ): PluginSources {
        val registry = PluginRegistry(plugins.filterIsInstance<NativePlugin>().toSet(), WatchConfig(enablePlugins = true).asFlow(), MapSettings())
        plugins.filterNot { it is NativePlugin }.forEach(registry::registerPlugin)
        return PluginSources(registry, { PermissionSubject(it, legacy = false) }, { _, p -> declared[p] ?: listOf(p) }) { caller, plugin, _ ->
            readAs += caller to plugin.pluginUuid
            upstream(plugin)
        }
    }

    private fun plugin(uuid: Uuid = READER, vararg sources: SourceDeclaration) =
        PluginMetadata(uuid, uuid.toString(), "", sources.toList(), emptyList())

    @Test
    fun aSourceNeedingNothingIsReadAsTheReader() = runTest {
        val read = sources(BuiltIn(listOf(source("preferences", "units")))).read(plugin(), SourceRequest("preferences", "units"))
        assertEquals(served(BUILT_IN), read)
        assertEquals(READER, (readAs.single().first as PluginCaller.App).subject.appUuid)
    }

    @Test
    fun aGuardedSourceNeedsEveryOneOfTheReadersSourcesToRequireTheSame() = runTest {
        val sources = sources(BuiltIn(listOf(source("preferences", "weather_location", "Location"))))
        val request = SourceRequest("preferences", "weather_location")

        assertEquals(served(BUILT_IN), sources.read(plugin(READER, source("weather", "location", "Location")), request))

        val leaky = sources.read(plugin(READER, source("weather", "location", "Location"), source("weather", "summary")), request)
        assertEquals(PluginErrors.PERMISSION_DENIED, assertIs<SourceUpdate.Error>(leaky).code)
    }

    @Test
    fun aReaderHoldingSomePackagesNeedsItsCallersToHoldAtLeastThose() = runTest {
        val maps = notificationsKey("com.maps")
        val sources = sources(BuiltIn(listOf(source("notifications", "notification", NOTIFICATIONS))))
        val request = SourceRequest("notifications", "notification")
        fun nav(vararg callerPermissions: String) = plugin(READER, source("navigation", "step", *callerPermissions))
        fun allowed(update: SourceUpdate) = update !is SourceUpdate.Error

        declared[NOTIFICATIONS] = listOf(maps)
        assertEquals(true, allowed(sources.read(nav(maps), request)))
        assertEquals(true, allowed(sources.read(nav(NOTIFICATIONS), request)))
        assertEquals(false, allowed(sources.read(nav(), request)))

        declared[NOTIFICATIONS] = listOf(NOTIFICATIONS)
        assertEquals(false, allowed(sources.read(nav(maps), request)))
        assertEquals(true, allowed(sources.read(nav(NOTIFICATIONS), request)))
    }

    @Test
    fun anotherJsPluginsSourceCanBeRead() = runTest {
        val read = sources(Js(OTHER, "Other", listOf(source("finance", "stock")))).read(plugin(), SourceRequest("finance", "stock"))
        assertEquals(served(OTHER), read)
    }

    @Test
    fun aPluginIsNeverAnsweredByItself() = runTest {
        val self = Js(READER, "Weather", listOf(source("weather", "location")))
        val other = Js(OTHER, "Other weather", listOf(source("weather", "location")))
        val read = sources(self, other).read(plugin(READER, *self.sources.toTypedArray()), SourceRequest("weather", "location", plugin = READER.toString()))
        assertEquals(served(OTHER), read)
    }

    @Test
    fun aLoopIsRefused() = runTest {
        val a = Js(READER, "A", listOf(source("a", "thing")))
        val b = Js(OTHER, "B", listOf(source("b", "thing")))
        val held = CompletableDeferred<Unit>()
        val sources = sources(a, b) { flow { emit(served(it.pluginUuid)); held.await() } }
        val aReadsB = async { sources.observe(plugin(READER, *a.sources.toTypedArray()), SourceRequest("b", "thing")).collect {} }
        runCurrent()

        val bReadsA = sources.read(plugin(OTHER, *b.sources.toTypedArray()), SourceRequest("a", "thing"))
        assertEquals(PluginErrors.INVALID_REQUEST, assertIs<SourceUpdate.Error>(bReadsA).code)

        held.complete(Unit)
        aReadsB.await()
        assertEquals(served(READER), sources.read(plugin(OTHER, *b.sources.toTypedArray()), SourceRequest("a", "thing")))
    }

    @Test
    fun nothingServingItIsUnavailable() = runTest {
        val read = sources().read(plugin(), SourceRequest("preferences", "units"))
        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, assertIs<SourceUpdate.Error>(read).code)
    }
}
