package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.permissions.internetKey
import kotlin.test.Test
import kotlin.test.assertEquals

private fun keyFor(url: String) = PluginNetworkPolicy.permissionFor(url)

private const val LAN = PluginPermissions.LOCAL_NETWORK

class PluginNetworkPolicyTest {

    @Test
    fun anInternetHostAnswersToItsOwnDomain() {
        assertEquals(internetKey("query1.finance.yahoo.com"), keyFor("https://query1.finance.yahoo.com/v8/finance/chart/AAPL"))
        assertEquals(internetKey("query1.finance.yahoo.com.evil.example.com"), keyFor("https://query1.finance.yahoo.com.evil.example.com/"))
    }

    @Test
    fun theLanIsItsOwnPermission() {
        listOf(
            "http://192.168.1.4/api/lights",
            "http://10.0.0.7/",
            "http://172.16.3.1/",
            "http://hue.local/api",
            "http://localhost:8080/",
            "http://[fd00::1]/api",
            "http://127.0.0.1:9000/",
            "http://169.254.169.254/latest/meta-data/",
        ).forEach { assertEquals(LAN, keyFor(it), it) }
    }

    @Test
    fun hostParsingIgnoresWhatIsNotTheHost() {
        val api = internetKey("api.example.com")
        assertEquals(api, keyFor("https://API.Example.COM/path?q=1#f"))
        assertEquals(api, keyFor("https://api.example.com:8443/path"))
        assertEquals(api, keyFor("https://api.example.com./path"))
        // Userinfo is where a URL can be made to read as one host and resolve to another.
        assertEquals(internetKey("evil.example.org"), keyFor("https://api.example.com@evil.example.org/"))
    }

    @Test
    fun anIpv6LiteralKeepsItsMeaningThroughParsing() {
        assertEquals(LAN, keyFor("http://[fd00::1]/api"))
        assertEquals(LAN, keyFor("http://[::1]:8080/"))
        assertEquals(internetKey("2606:4700:4700::1111"), keyFor("http://[2606:4700:4700::1111]/"))
    }

    @Test
    fun anAddressWrittenToLookLikeTheInternetIsNeverTheInternet() {
        // A resolver takes all of these as 127.0.0.1 or 192.168.1.1: each is either the LAN or refused.
        assertEquals(LAN, keyFor("http://[::ffff:192.168.1.1]/"))
        assertEquals(LAN, keyFor("http://[::ffff:c0a8:101]/"))
        listOf(
            "http://2130706433/",
            "http://0x7f000001/",
            "http://127.1/",
            "http://0177.0.0.1/",
        ).forEach { assertEquals(null, keyFor(it), it) }
    }

    @Test
    fun aHostnameThatMerelyContainsDigitsIsStillAHostname() {
        assertEquals(internetKey("s3.eu-west-2.amazonaws.com"), keyFor("https://s3.eu-west-2.amazonaws.com/"))
        assertEquals(internetKey("1password.com"), keyFor("https://1password.com/"))
    }

    @Test
    fun aWebSocketAnswersToTheSamePermissionsAsHttp() {
        assertEquals(internetKey("stream.example.com"), keyFor("wss://stream.example.com/v1/ticks"))
        assertEquals(internetKey("stream.example.com"), keyFor("ws://stream.example.com:8080/"))
        assertEquals(LAN, keyFor("ws://192.168.1.4:81/"))
    }

    @Test
    fun onlyHttpUrlsAreRequestable() {
        listOf("file:///etc/passwd", "content://media/external/images", "/relative/path", "")
            .forEach { assertEquals(null, keyFor(it), it) }
    }
}
