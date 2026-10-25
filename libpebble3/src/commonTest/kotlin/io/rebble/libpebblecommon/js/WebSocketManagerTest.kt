package io.rebble.libpebblecommon.js

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.Headers
import io.rebble.libpebblecommon.permissions.NetworkAccess
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

private const val INSTANCE = "WebSocket._instances.get(1)"
private val ABNORMAL_CLOSE = "$INSTANCE._onClose(1006, '', false)"

/** The mock engine answers on a real thread, so wait on the socket's job rather than virtual time. */
private suspend fun TestScope.awaitSockets() = coroutineContext.job.children.toList().joinAll()

class WebSocketManagerTest {

    private val internet = NetworkAccess { _, _ -> PluginNetworkVerdict.Allowed }
    private val nothing = NetworkAccess { _, _ -> PluginNetworkVerdict.Denied("not allowed") }

    @Test
    fun aBlockedHostIsNeverDialled() = runTest {
        var dialled = false
        val client = HttpClient(MockEngine { dialled = true; respond("") }) { install(WebSockets) }
        val evals = mutableListOf<String>()
        val manager = WebSocketManager(this, { evals.add(it) }, client, Uuid.random(), nothing)

        assertEquals(1, manager.dispatch("createInstance", listOf("wss://stream.example.com/", "", """{"Authorization":"Bearer t"}""")))
        awaitSockets()

        assertFalse(dialled)
        assertEquals(listOf("$INSTANCE._onError()", ABNORMAL_CLOSE), evals)
    }

    @Test
    fun aFailedHandshakeReportsErrorThenClose() = runTest {
        // The mock engine answers 200 to the upgrade, which is not a websocket.
        val client = HttpClient(MockEngine { respond("") }) { install(WebSockets) }
        val evals = mutableListOf<String>()
        val manager = WebSocketManager(this, { evals.add(it) }, client, Uuid.random(), internet)

        manager.dispatch("createInstance", listOf("wss://stream.example.com/", ""))
        awaitSockets()

        assertEquals(listOf("$INSTANCE._onError()", ABNORMAL_CLOSE), evals)
    }

    @Test
    fun handshakeCarriesTheRequestedHeadersButNotUpgradeOnes() = runTest {
        var sent: Headers? = null
        val client = HttpClient(MockEngine { sent = it.headers; respond("") }) { install(WebSockets) }
        val manager = WebSocketManager(this, {}, client, Uuid.random(), internet)

        manager.dispatch(
            "createInstance",
            listOf(
                "wss://stream.example.com/",
                "chat",
                """{"Authorization":"Bearer t","Host":"evil.example","Connection":"close","Upgrade":"h2c",""" +
                    """"Sec-WebSocket-Key":"x","Sec-WebSocket-Protocol":"other"}""",
            ),
        )
        awaitSockets()

        val headers = sent!!
        assertEquals(listOf("Bearer t"), headers.getAll("Authorization"))
        assertFalse(headers.getAll("Host").orEmpty().contains("evil.example"))
        assertFalse(headers.getAll("Connection").orEmpty().contains("close"))
        assertFalse(headers.getAll("Upgrade").orEmpty().contains("h2c"))
        assertFalse(headers.getAll("Sec-WebSocket-Key").orEmpty().contains("x"))
        assertEquals(listOf("chat"), headers.getAll("Sec-WebSocket-Protocol"))
    }

    @Test
    fun malformedHeadersAreIgnored() = runTest {
        var sent: Headers? = null
        val client = HttpClient(MockEngine { sent = it.headers; respond("") }) { install(WebSockets) }
        val manager = WebSocketManager(this, {}, client, Uuid.random(), internet)

        manager.dispatch("createInstance", listOf("wss://stream.example.com/", "", "not json"))
        awaitSockets()

        assertEquals(null, sent?.get("Authorization"))
    }

    @Test
    fun closingWhileConnectingGivesUpOnTheConnect() = runTest {
        val client = HttpClient(MockEngine { awaitCancellation() }) { install(WebSockets) }
        val evals = mutableListOf<String>()
        val manager = WebSocketManager(this, { evals.add(it) }, client, Uuid.random(), internet)

        manager.dispatch("createInstance", listOf("wss://stream.example.com/", ""))
        runCurrent()
        manager.dispatch("close", listOf(1.0, 1000.0, ""))
        awaitSockets()

        assertEquals(listOf(ABNORMAL_CLOSE), evals)
    }

    // Unconfined, like the app's immediate dispatcher: a cancelled socket drops out of the map mid-loop.
    @Test
    fun tearingDownWithSeveralSocketsOpenClosesThemAll() = runTest(UnconfinedTestDispatcher()) {
        val client = HttpClient(MockEngine { awaitCancellation() }) { install(WebSockets) }
        val evals = mutableListOf<String>()
        val manager = WebSocketManager(this, { evals.add(it) }, client, Uuid.random(), internet)

        manager.dispatch("createInstance", listOf("wss://a.example.com/", ""))
        manager.dispatch("createInstance", listOf("wss://b.example.com/", ""))
        runCurrent()
        manager.close()
        awaitSockets()

        assertEquals(2, evals.count { it.endsWith("._onClose(1006, '', false)") })
    }

    @Test
    fun tearingDownTheSessionClosesEverySocket() = runTest {
        val client = HttpClient(MockEngine { awaitCancellation() }) { install(WebSockets) }
        val evals = mutableListOf<String>()
        val manager = WebSocketManager(this, { evals.add(it) }, client, Uuid.random(), internet)

        manager.dispatch("createInstance", listOf("wss://stream.example.com/", ""))
        runCurrent()
        manager.close()
        awaitSockets()

        assertEquals(listOf(ABNORMAL_CLOSE), evals)
    }
}
