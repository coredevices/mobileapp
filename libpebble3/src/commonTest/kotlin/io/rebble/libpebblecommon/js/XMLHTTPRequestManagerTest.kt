package io.rebble.libpebblecommon.js

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.rebble.libpebblecommon.permissions.DataUsageRecorder
import io.rebble.libpebblecommon.permissions.NetworkAccess
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val APP = Uuid.parse("00000000-0000-0000-0000-00000000b001")
private const val URL = "https://api.example.com/weather"

private data class Recorded(val url: String, val sent: Long, val received: Long, val requests: Int)

/** The mock engine answers on a real thread, so wait on the request's job rather than virtual time. */
private suspend fun TestScope.awaitRequests() = coroutineContext.job.children.toList().joinAll()

class XMLHTTPRequestManagerTest {
    private val recorded = mutableListOf<Recorded>()
    private val recorder = DataUsageRecorder { _, url, sent, received, requests -> recorded += Recorded(url, sent, received, requests) }
    private val internet = NetworkAccess { _, _ -> PluginNetworkVerdict.Allowed }
    private val nothing = NetworkAccess { _, _ -> PluginNetworkVerdict.Denied("not allowed") }
    private val sentBodies = mutableListOf<ByteArray>()
    private val client = HttpClient(MockEngine { request ->
        sentBodies += request.body.toByteArray()
        respond("{\"temp\":12}")
    })

    private fun CoroutineScope.manager(
        evals: MutableList<String>,
        access: NetworkAccess = internet,
        interceptor: HttpInterceptor? = null,
    ) = XMLHTTPRequestManager(this, { evals += it }, HttpInterceptorManager(listOfNotNull(interceptor), recorder), APP, client, access, recorder)

    private fun interceptor(servedLocally: Boolean) = object : HttpInterceptor {
        override val servedLocally = servedLocally
        override fun shouldIntercept(url: String) = true
        override suspend fun onIntercepted(url: String, method: String, body: String?, appUuid: Uuid) =
            InterceptResponse("{\"temp\":3}", 200)
    }

    private fun XMLHTTPRequestManager.request(async: Boolean, body: String? = null, binary: Boolean = false): Any? {
        if (dispatch("getXHRInstanceID", emptyList()) != 1) error("one request per test")
        return reopen(async, body, binary)
    }

    private fun XMLHTTPRequestManager.reopen(async: Boolean, body: String? = null, binary: Boolean = false): Any? {
        dispatch("open", listOf(1.0, if (body == null) "GET" else "POST", URL, async, "", ""))
        return dispatch("send", listOf(1.0, "", body, binary))
    }

    @Test
    fun anAsyncRequestsBytesAreRecorded() = runTest {
        val evals = mutableListOf<String>()
        assertNull(manager(evals).request(async = true, body = "city=Oslo"))
        awaitRequests()

        assertEquals(listOf(Recorded(URL, "POST".length + URL.length + 9L, 11, 1)), recorded)
        assertTrue(evals.any { it.contains("_onResponseComplete") })
    }

    @Test
    fun aSyncRequestIsAnsweredByTheSendCall() = runTest {
        val evals = mutableListOf<String>()
        val result = Json.decodeFromString<JsonObject>(manager(evals).request(async = false) as String)

        assertEquals("load", result["type"]!!.jsonPrimitive.content)
        assertEquals(200, result["status"]!!.jsonPrimitive.int)
        assertEquals("{\"temp\":12}", result["body"]!!.jsonPrimitive.content)
        assertTrue(evals.none { it.contains("_onResponseComplete") })
        assertEquals(listOf(Recorded(URL, "GET".length + URL.length.toLong(), 11, 1)), recorded)
    }

    @Test
    fun aBlockedSyncRequestSaysSoAndIsNotCounted() = runTest {
        val result = manager(mutableListOf(), nothing).request(async = false) as String

        assertEquals("""{"type":"error","notAllowed":true}""", result)
        assertEquals(emptyList(), recorded)
    }

    @Test
    fun aBinaryBodyCrossesAsBase64AndIsSentAsBytes() = runTest {
        manager(mutableListOf()).request(async = false, body = "AAEC/w==", binary = true)

        assertContentEquals(byteArrayOf(0, 1, 2, -1), sentBodies.single())
        assertEquals("POST".length + URL.length + 4L, recorded.single().sent)
    }

    @Test
    fun abortingAnAnsweredRequestChangesNothing() = runTest {
        val evals = mutableListOf<String>()
        val manager = manager(evals)
        manager.request(async = true)
        awaitRequests()
        val answered = evals.toList()

        manager.dispatch("abort", listOf(1.0))

        assertEquals(answered, evals)
    }

    @Test
    fun aFinishedRequestCanBeOpenedAgain() = runTest {
        val manager = manager(mutableListOf())
        manager.request(async = false)

        val again = Json.decodeFromString<JsonObject>(manager.reopen(async = false) as String)

        assertEquals("load", again["type"]!!.jsonPrimitive.content)
        assertEquals(2, recorded.size)
    }

    @Test
    fun anInterceptedRequestCountsForTheApp() = runTest {
        manager(mutableListOf(), interceptor = interceptor(servedLocally = false)).request(async = false)

        assertEquals(listOf(Recorded(URL, "GET".length + URL.length.toLong(), 10, 1)), recorded)
        assertEquals(emptyList(), sentBodies)
    }

    @Test
    fun aRequestServedFromThePhoneIsNotCounted() = runTest {
        manager(mutableListOf(), interceptor = interceptor(servedLocally = true)).request(async = false)

        assertEquals(emptyList(), recorded)
    }

    @Test
    fun noHostHeaderMeansNothingExtraToCheck() {
        assertNull(hostHeaderUrl("https://api.example.com/v1", mapOf("Accept" to "application/json")))
        assertNull(hostHeaderUrl("https://api.example.com/v1", mapOf("Host" to " ")))
    }

    @Test
    fun hostHeaderIsCheckedOnTheRequestScheme() {
        assertEquals(
            "https://other.example.org/",
            hostHeaderUrl("https://api.example.com/v1?x=1", mapOf("Host" to "other.example.org")),
        )
        assertEquals(
            "http://192.168.1.10:8080/",
            hostHeaderUrl("http://cdn.example.com/", mapOf("host" to "192.168.1.10:8080")),
        )
    }

    @Test
    fun hostHeaderNameIsCaseInsensitive() {
        assertEquals(
            "https://other.example.org/",
            hostHeaderUrl("https://api.example.com/", mapOf("HOST" to "other.example.org")),
        )
    }
}
