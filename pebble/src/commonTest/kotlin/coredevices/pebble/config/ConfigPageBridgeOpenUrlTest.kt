package coredevices.pebble.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigPageBridgeOpenUrlTest {
    private fun bridgeTest(block: suspend (bridge: ConfigPageBridge, asked: MutableList<String>, replies: MutableList<String>) -> Unit) =
        runTest {
            val replies = mutableListOf<String>()
            val asked = mutableListOf<String>()
            val bridge = ConfigPageBridge(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
            bridge.attach { replies += it }
            bridge.openUrl = { url -> asked += url; true }
            block(bridge, asked, replies)
        }

    @Test
    fun aWebUrlIsHandedToTheUser() = bridgeTest { bridge, asked, replies ->
        bridge.onCall("""{"id":1,"method":"openUrl","message":"https://github.com/coredevices/pebble-plugins"}""")
        assertEquals(listOf("https://github.com/coredevices/pebble-plugins"), asked)
        assertTrue(replies.last().contains("window.__pebbleConfig.reply(1, \"true\")"))
    }

    @Test
    fun otherSchemesAreRefused() = bridgeTest { bridge, asked, replies ->
        listOf(
            "javascript:alert(1)", "intent://x#Intent;end", "file:///etc/hosts", "github.com/x",
            "https://trusted.com@evil.com/", "https://a.b/x y", "https://a.b/\\n", "https://a.b/\\u202Emoc",
            "https://\u0430pple.com/", "https://g%69thub.com/",
        ).forEachIndexed { i, url ->
            bridge.onCall("""{"id":$i,"method":"openUrl","message":"$url"}""")
            assertTrue(replies.last().contains("openUrl needs an http(s) url"), url)
        }
        bridge.onCall("""{"id":9,"method":"openUrl","message":{"url":"https://a.b"}}""")
        assertTrue(replies.last().contains("openUrl needs an http(s) url"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun declinedResolvesFalse() = bridgeTest { bridge, _, replies ->
        bridge.openUrl = { false }
        bridge.onCall("""{"id":1,"method":"openUrl","message":"HTTPS://A.B/x"}""")
        assertTrue(replies.last().contains("window.__pebbleConfig.reply(1, \"false\")"))
    }

    @Test
    fun withoutAHandlerOpeningIsRefused() = bridgeTest { bridge, _, replies ->
        bridge.openUrl = null
        bridge.onCall("""{"id":1,"method":"openUrl","message":"https://a.b"}""")
        assertTrue(replies.last().contains("not available"))
    }
}
