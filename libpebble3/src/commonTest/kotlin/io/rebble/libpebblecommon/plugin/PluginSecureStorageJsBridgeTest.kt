package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginSecureStorageJsBridgeTest {
    private val storage = object : SecureStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun set(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
        override suspend fun destroy() = values.clear()
    }

    private fun bridgeTest(block: suspend (request: (String) -> String) -> Unit) = runTest {
        val replies = mutableListOf<String>()
        val bridge = PluginSecureStorageJsBridge(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            { replies += it },
            storage,
        )
        var nextId = 0
        block { json ->
            bridge.dispatch("request", listOf(++nextId, json))
            replies.last()
        }
    }

    private fun reply(id: Int, payload: String) = "globalThis.__pluginSecureStorageReply($id,$payload)"

    @Test
    fun setThenGetReturnsTheValue() = bridgeTest { request ->
        assertEquals(reply(1, """{"result":null}"""), request("""{"operation":"setItem","key":"token","value":"s3cret"}"""))
        assertEquals(reply(2, """{"result":"s3cret"}"""), request("""{"operation":"getItem","key":"token"}"""))
    }

    @Test
    fun aMissingOrRemovedKeyIsNull() = bridgeTest { request ->
        storage.values["token"] = "s3cret"
        request("""{"operation":"removeItem","key":"token"}""")
        assertEquals(reply(2, """{"result":null}"""), request("""{"operation":"getItem","key":"token"}"""))
    }

    @Test
    fun anEmptyValueIsStored() = bridgeTest { request ->
        request("""{"operation":"setItem","key":"token","value":""}""")
        assertEquals("", storage.values["token"])
    }

    @Test
    fun invalidRequestsRejectWithInvalidArgs() = bridgeTest { request ->
        assertEquals(
            reply(1, """{"error":{"code":"INVALID_ARGS","message":"value is required"}}"""),
            request("""{"operation":"setItem","key":"token"}"""),
        )
        assertEquals(
            reply(2, """{"error":{"code":"INVALID_ARGS","message":"key is required"}}"""),
            request("""{"operation":"getItem"}"""),
        )
    }
}
