package io.rebble.libpebblecommon.plugin

import com.russhwolf.settings.MapSettings
import io.rebble.libpebblecommon.LibPebbleAnalytics
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.permissions.APP
import io.rebble.libpebblecommon.permissions.FakePermissionDao
import io.rebble.libpebblecommon.permissions.NoWatches
import io.rebble.libpebblecommon.permissions.RunningApps
import io.rebble.libpebblecommon.permissions.notificationsKey
import io.rebble.libpebblecommon.permissions.permissions
import io.rebble.libpebblecommon.permissions.subject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val PLUGIN = Uuid.parse("00000000-0000-0000-0002-0000000000c1")

private val ENVELOPE = SourceEnvelope(PLUGIN.toString(), null, emptyList())

private class ClientFakePlugin(
    callerPermissions: List<PluginPermission> = emptyList(),
    properties: Map<String, List<String>> = emptyMap(),
    private val envelopes: Flow<SourceUpdate> = flow { emit(SourceUpdate.Data(ENVELOPE)) },
    private val onInvoke: suspend () -> ActionResult = { ActionResult(ok = true) },
) : Plugin {
    override val pluginUuid = PLUGIN
    override val name = "Fake"
    override val description = ""
    override val sources = listOf(SourceDeclaration("weather", listOf("now"), properties, callerPermissions = callerPermissions))
    override val actions = listOf(
        ActionDeclaration(
            "do_it",
            parameters = JsonObject(mapOf("required" to JsonArray(listOf(JsonPrimitive("what"))))),
            callerPermissions = callerPermissions,
        ),
    )
    val invoked = mutableListOf<JsonObject>()
    val prompting = mutableListOf<Boolean>()

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        prompt: Boolean,
    ): Flow<SourceUpdate> = flow {
        prompting += prompt
        envelopes.collect { emit(it) }
    }

    override suspend fun invoke(action: String, args: JsonObject, prompt: Boolean): ActionResult {
        prompting += prompt
        invoked += args
        return onInvoke()
    }
}

private class CountingAnalytics : LibPebbleAnalytics {
    val counters = mutableListOf<String>()
    override fun logEvent(name: String, parameters: Map<String, String>) {}
    override fun incrementHeartbeatCounter(name: String) {
        counters += name
    }
}

private val ARGS = JsonObject(mapOf("what" to JsonPrimitive("thing")))
private val APP_CALLER = PluginCaller.App(subject(), "App")

class PluginClientTest {
    private val dao = FakePermissionDao()
    private val running = RunningApps(NoWatches)
    private val analytics = CountingAnalytics()
    private val logs = PluginLogs()
    private val registry = PluginRegistry(emptySet(), WatchConfig(enablePlugins = true).asFlow(), MapSettings())
    private val client = PluginClient(registry, permissions(dao, running = running), running, analytics, logs)

    private fun ClientFakePlugin.registered(): PluginMetadata = also { registry.registerPlugin(it) }.metadata

    @Test
    fun theHostInvokesWithoutCallerPermissionsAndIsCounted() = runTest {
        val plugin = ClientFakePlugin(callerPermissions = listOf(PluginPermission(PluginPermissions.CALENDAR)))

        val result = client.invoke(PluginCaller.Host(), plugin.registered(), "do_it", ARGS)

        assertTrue(result.ok)
        assertEquals(listOf(ARGS), plugin.invoked)
        assertEquals(listOf(true), plugin.prompting)
        assertEquals(listOf(PluginHeartbeatCounters.HOST_INVOCATIONS), analytics.counters)
    }

    @Test
    fun anAppIsHeldToTheActionsCallerPermissions() = runTest {
        val plugin = ClientFakePlugin(callerPermissions = listOf(PluginPermission(PluginPermissions.CALENDAR)))
        dao.insertOrReplace(deniedRow(PluginPermissions.CALENDAR))

        val result = client.invoke(APP_CALLER, plugin.registered(), "do_it", ARGS)

        assertEquals(PluginErrors.PERMISSION_DENIED, result.code)
        assertEquals(emptyList(), plugin.invoked)
        assertEquals(emptyList(), analytics.counters)
    }

    @Test
    fun appInvocationsAreCountedSeparately() = runTest {
        client.invoke(APP_CALLER, ClientFakePlugin().registered(), "do_it", ARGS)

        assertEquals(listOf(PluginHeartbeatCounters.PKJS_INVOCATIONS), analytics.counters)
    }

    @Test
    fun missingRequiredParamsNeverReachThePlugin() = runTest {
        val plugin = ClientFakePlugin()

        val result = client.invoke(PluginCaller.Host(), plugin.registered(), "do_it", JsonObject(emptyMap()))

        assertEquals(PluginErrors.INVALID_ARGS, result.code)
        assertEquals(emptyList(), plugin.invoked)
    }

    @Test
    fun anUnknownActionIsUnavailable() = runTest {
        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, client.invoke(PluginCaller.Host(), ClientFakePlugin().registered(), "nope", ARGS).code)
    }

    @Test
    fun anActionThatNeverAnswersTimesOutAndStopsRunning() = runTest {
        val plugin = ClientFakePlugin(onInvoke = { awaitCancellation() })

        val result = client.invoke(PluginCaller.Host(), plugin.registered(), "do_it", ARGS, timeout = 5.seconds)

        assertEquals(PluginErrors.TIMEOUT, result.code)
        assertEquals(emptySet(), running.running.first())
    }

    @Test
    fun anActionThatThrowsFailsCleanly() = runTest {
        val plugin = ClientFakePlugin(onInvoke = { error("boom") })

        val result = client.invoke(PluginCaller.Host(), plugin.registered(), "do_it", ARGS)

        assertEquals(PluginErrors.UNKNOWN, result.code)
        assertEquals("boom", result.message)
    }

    @Test
    fun aProbeNeverPromptsAndIsNotCounted() = runTest {
        val plugin = ClientFakePlugin()

        val update = client.read(PluginCaller.Host(probe = true), plugin.registered(), "weather", "now")

        assertEquals(SourceUpdate.Data(ENVELOPE), update)
        assertEquals(listOf(false), plugin.prompting)
        assertEquals(emptyList(), analytics.counters)
    }

    @Test
    fun readingCountsAnObservation() = runTest {
        client.read(PluginCaller.Host(), ClientFakePlugin().registered(), "weather", "now")
        client.read(APP_CALLER, ClientFakePlugin().registered(), "weather", "now")

        assertEquals(
            listOf(PluginHeartbeatCounters.HOST_OBSERVATIONS, PluginHeartbeatCounters.PKJS_OBSERVATIONS),
            analytics.counters,
        )
    }

    @Test
    fun aReadThatNeverAnswersTimesOut() = runTest {
        val plugin = ClientFakePlugin(envelopes = flow { awaitCancellation() })

        val update = client.read(PluginCaller.Host(), plugin.registered(), "weather", "now", timeout = 5.seconds)

        assertEquals(PluginErrors.TIMEOUT, assertIs<SourceUpdate.Error>(update).code)
        assertEquals(emptySet(), running.running.first())
    }

    @Test
    fun aSourceThatEndsWithoutDataIsAnError() = runTest {
        val update = client.read(PluginCaller.Host(), ClientFakePlugin(envelopes = emptyFlow()).registered(), "weather", "now")

        assertEquals(PluginErrors.UNKNOWN, assertIs<SourceUpdate.Error>(update).code)
    }

    @Test
    fun aSourceThatThrowsBecomesAnError() = runTest {
        val plugin = ClientFakePlugin(envelopes = flow { error("broken") })

        val update = client.read(PluginCaller.Host(), plugin.registered(), "weather", "now")

        assertEquals(SourceUpdate.Error(PluginErrors.UNKNOWN, "broken"), update)
    }

    @Test
    fun aPluginErrorDoesNotEndTheObservation() = runTest {
        val failed = SourceUpdate.Error(PluginErrors.TIMEOUT, "plugin did not respond")
        val plugin = ClientFakePlugin(envelopes = flow {
            emit(failed)
            emit(SourceUpdate.Data(ENVELOPE))
        })

        val updates = client.observe(PluginCaller.Host(), plugin.registered(), "weather", "now").toList()

        assertEquals(listOf(failed, SourceUpdate.Data(ENVELOPE)), updates)
    }

    @Test
    fun subscribersOnlyGetWhatTheSourceDeclares() = runTest {
        val undeclared = SourceInstance("1", mapOf("colour" to mapOf("shortText" to JsonPrimitive("red"))))
        val plugin = ClientFakePlugin(envelopes = flow { emit(SourceUpdate.Data(ENVELOPE.copy(instances = listOf(undeclared)))) })

        val update = client.read(PluginCaller.Host(), plugin.registered(), "weather", "now")

        assertEquals(emptyMap(), assertIs<SourceUpdate.Data>(update).envelope.instances.single().properties)
    }

    @Test
    fun aPluginsDeveloperIsToldWhatWasDropped() = runTest {
        val logged = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { logs.messages.collect { logged += it } }
        val undeclared = SourceInstance("1", mapOf("colour" to mapOf("shortText" to JsonPrimitive("red"))))
        val plugin = ClientFakePlugin(envelopes = flow { emit(SourceUpdate.Data(ENVELOPE.copy(instances = listOf(undeclared)))) })

        client.read(PluginCaller.Host(), plugin.registered(), "weather", "now")

        assertEquals(listOf("[plugin] Fake: weather/now.colour: dropped: shortText isn't declared"), logged)
    }

    @Test
    fun anUnknownSourceIsUnavailable() = runTest {
        val update = client.read(PluginCaller.Host(), ClientFakePlugin().registered(), "weather", "tomorrow")

        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, assertIs<SourceUpdate.Error>(update).code)
    }

    @Test
    fun thePluginRunsOnlyWhileObserved() = runTest {
        val plugin = ClientFakePlugin(envelopes = flow {
            emit(SourceUpdate.Data(ENVELOPE))
            awaitCancellation()
        })

        client.observe(PluginCaller.Host(), plugin.registered(), "weather", "now").take(1).collect {
            assertEquals(setOf(PLUGIN), running.running.first())
        }

        assertEquals(emptySet(), running.running.first())
    }

    @Test
    fun anAppRefusedASourceIsToldWhy() = runTest {
        val plugin = ClientFakePlugin(callerPermissions = listOf(PluginPermission(PluginPermissions.CALENDAR)))
        dao.insertOrReplace(deniedRow(PluginPermissions.CALENDAR))

        val updates = client.observe(APP_CALLER, plugin.registered(), "weather", "now").take(1).toList()

        assertEquals(PluginErrors.PERMISSION_DENIED, assertIs<SourceUpdate.Error>(updates.single()).code)
        assertEquals(emptyList(), plugin.prompting)
    }

    @Test
    fun anUninstalledPluginIsUnavailable() = runTest {
        val gone = ClientFakePlugin().metadata

        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, client.invoke(PluginCaller.Host(), gone, "do_it", ARGS).code)
        val update = client.read(PluginCaller.Host(), gone, "weather", "now")
        assertEquals(PluginErrors.PLUGIN_UNAVAILABLE, assertIs<SourceUpdate.Error>(update).code)
    }

    @Test
    fun anAppHoldingSomePackagesSeesOnlyTheirInstances() = runTest {
        fun instance(id: String, packageName: String) = SourceInstance(
            id,
            mapOf(SourceInstance.PROPERTY_PACKAGE to mapOf(SourceShapeNames.LONG_TEXT to JsonObject(mapOf("text" to JsonPrimitive(packageName))))),
        )
        val shade = SourceEnvelope(PLUGIN.toString(), null, listOf(instance("1", "com.maps"), instance("2", "com.chat")))
        val plugin = ClientFakePlugin(
            callerPermissions = listOf(PluginPermission(PluginPermissions.NOTIFICATIONS)),
            properties = mapOf(SourceInstance.PROPERTY_PACKAGE to listOf(SourceShapeNames.LONG_TEXT)),
            envelopes = flow { emit(SourceUpdate.Data(shade)) },
        ).registered()

        dao.insertOrReplace(LockerAppPermission(APP, notificationsKey("com.maps"), granted = true, requestedAtMs = 1, decidedAtMs = 1))
        val scoped = assertIs<SourceUpdate.Data>(client.observe(APP_CALLER, plugin, "weather", "now").first())
        assertEquals(listOf("1"), scoped.envelope.instances.map { it.instanceId })

        dao.insertOrReplace(LockerAppPermission(APP, PluginPermissions.NOTIFICATIONS, granted = true, requestedAtMs = 1, decidedAtMs = 1))
        val all = assertIs<SourceUpdate.Data>(client.observe(APP_CALLER, plugin, "weather", "now").first())
        assertEquals(listOf("1", "2"), all.envelope.instances.map { it.instanceId })
    }

    @Test
    fun aPartialGrantIsRefusedBySourcesThatCantBeNarrowed() = runTest {
        val plugin = ClientFakePlugin(callerPermissions = listOf(PluginPermission(PluginPermissions.NOTIFICATIONS))).registered()
        dao.insertOrReplace(LockerAppPermission(APP, notificationsKey("com.maps"), granted = true, requestedAtMs = 1, decidedAtMs = 1))

        val update = client.observe(APP_CALLER, plugin, "weather", "now").first()

        val error = assertIs<SourceUpdate.Error>(update)
        assertEquals(PluginErrors.PERMISSION_DENIED, error.code)
        assertEquals("App holds Notifications/com.maps, but weather/now needs Notifications", error.message)
    }

    private fun deniedRow(permission: String) =
        LockerAppPermission(APP, permission, granted = false, requestedAtMs = 1, decidedAtMs = 1)
}
