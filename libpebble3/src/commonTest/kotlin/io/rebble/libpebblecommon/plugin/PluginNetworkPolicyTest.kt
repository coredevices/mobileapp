package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun policy(vararg permissions: PluginPermission) = PluginNetworkPolicy(permissions.toList())

private fun internet(vararg domains: String) = PluginPermission(
    PluginPermissions.INTERNET,
    if (domains.isEmpty()) emptyMap() else mapOf(PluginPermissions.PARAM_DOMAINS to domains.toList()),
)

private val localNetwork = PluginPermission(PluginPermissions.LOCAL_NETWORK)

private const val UUID = "6f9c1a44-3d1e-4b8a-9c2f-0d5e7a1b3c40"

private fun PluginNetworkPolicy.allows(url: String) = check(url) is PluginNetworkVerdict.Allowed

class PluginNetworkPolicyTest {

    @Test
    fun aPluginThatAskedForNothingReachesNothing() {
        val policy = policy()
        assertTrue(!policy.allows("https://query1.finance.yahoo.com/v8/finance/chart/AAPL"))
        assertTrue(!policy.allows("http://192.168.1.4/api/lights"))
    }

    @Test
    fun internetIsNarrowedToTheDomainsDeclared() {
        val policy = policy(internet("query1.finance.yahoo.com"))
        assertTrue(policy.allows("https://query1.finance.yahoo.com/v8/finance/chart/AAPL"))
        assertTrue(!policy.allows("https://evil.example.com/collect"))
        // The obvious dodges: a lookalike host, and the declared name as a prefix or a subdomain
        // of someone else's domain.
        assertTrue(!policy.allows("https://query1.finance.yahoo.com.evil.example.com/"))
        assertTrue(!policy.allows("https://notquery1.finance.yahoo.com/"))
    }

    @Test
    fun aDeclaredDomainCoversItsSubdomains() {
        val policy = policy(internet("meethue.com"))
        assertTrue(policy.allows("https://discovery.meethue.com/"))
        assertTrue(policy.allows("https://meethue.com/"))
        assertTrue(!policy.allows("https://meethue.com.example.com/"))
    }

    @Test
    fun internetWithNoDomainsIsTheWholeWebButStillNotTheLan() {
        val policy = policy(internet())
        assertTrue(policy.allows("https://anything.example.com/"))
        assertTrue(!policy.allows("http://192.168.1.4/api"))
    }

    @Test
    fun theLanIsItsOwnPermission() {
        val lanOnly = policy(localNetwork)
        assertTrue(lanOnly.allows("http://192.168.1.4/api/lights"))
        assertTrue(lanOnly.allows("http://10.0.0.7/"))
        assertTrue(lanOnly.allows("http://172.16.3.1/"))
        assertTrue(lanOnly.allows("http://hue.local/api"))
        assertTrue(lanOnly.allows("http://localhost:8080/"))
        assertTrue(lanOnly.allows("http://[fd00::1]/api"))
        // Internet access does not come with it.
        assertTrue(!lanOnly.allows("https://discovery.meethue.com/"))
    }

    @Test
    fun internetDoesNotReachTheLan() {
        // The exfiltration shape this is for: a plugin allowed one API host must not be able to
        // sweep the user's own network.
        val policy = policy(internet("api.example.com"))
        assertTrue(!policy.allows("http://192.168.1.1/"))
        assertTrue(!policy.allows("http://127.0.0.1:9000/"))
        assertTrue(!policy.allows("http://169.254.169.254/latest/meta-data/"))
        assertTrue(!policy.allows("http://printer.local/"))
    }

    @Test
    fun hostParsingIgnoresWhatIsNotTheHost() {
        val policy = policy(internet("api.example.com"))
        assertTrue(policy.allows("https://API.Example.COM/path?q=1#f"))
        assertTrue(policy.allows("https://api.example.com:8443/path"))
        assertTrue(policy.allows("https://api.example.com./path"))
        // Userinfo is where a URL can be made to read as one host and resolve to another.
        assertTrue(!policy.allows("https://api.example.com@evil.example.org/"))
    }

    @Test
    fun anIpv6LiteralKeepsItsMeaningThroughParsing() {
        // ktor hands back the brackets; the private-range check sees a bare address.
        val lanOnly = policy(localNetwork)
        assertTrue(lanOnly.allows("http://[fd00::1]/api"))
        assertTrue(lanOnly.allows("http://[::1]:8080/"))
        assertTrue(!policy(internet()).allows("http://[fd00::1]/api"))
        // A public v6 address is the internet, not the LAN.
        assertTrue(!lanOnly.allows("http://[2606:4700:4700::1111]/"))
        assertTrue(policy(internet()).allows("http://[2606:4700:4700::1111]/"))
    }

    @Test
    fun anAddressWrittenToLookLikeTheInternetIsStillTheLan() {
        // A resolver takes all of these as 127.0.0.1 or 192.168.1.1. Each is a way to reach the
        // user's own network from a plugin that was only allowed the internet, so a host that is
        // numeric but not four plain octets is refused outright rather than range-checked.
        val internetOnly = policy(internet())
        listOf(
            "http://[::ffff:192.168.1.1]/",   // IPv4-mapped IPv6
            "http://[::ffff:c0a8:101]/",      // the same, in hex
            "http://2130706433/",             // 127.0.0.1 as one decimal integer
            "http://0x7f000001/",             // ...as hex
            "http://127.1/",                  // ...short form
            "http://0177.0.0.1/",             // ...with an octal octet
        ).forEach { url ->
            assertTrue(!internetOnly.allows(url), "$url must not be reachable with Internet alone")
        }
        // And the two canonical spellings a LAN plugin legitimately uses still work.
        val lanOnly = policy(localNetwork)
        assertTrue(lanOnly.allows("http://[::ffff:192.168.1.1]/"))
        assertTrue(lanOnly.allows("http://192.168.1.1/"))
    }

    @Test
    fun aHostnameThatMerelyContainsDigitsIsStillAHostname() {
        val policy = policy(internet())
        assertTrue(policy.allows("https://query1.finance.yahoo.com/"))
        assertTrue(policy.allows("https://s3.eu-west-2.amazonaws.com/"))
        assertTrue(policy.allows("https://1password.com/"))
    }

    @Test
    fun onlyHttpUrlsAreRequestable() {
        val policy = policy(internet())
        assertTrue(!policy.allows("file:///etc/passwd"))
        assertTrue(!policy.allows("content://media/external/images"))
        assertTrue(!policy.allows("/relative/path"))
        assertTrue(!policy.allows(""))
    }

    @Test
    fun aDenialSaysWhatWasMissing() {
        val denied = policy(internet("api.example.com")).check("https://elsewhere.example.com/")
        assertTrue(denied is PluginNetworkVerdict.Denied)
        assertEquals(
            "elsewhere.example.com is not one of the domains this plugin declared",
            (denied as PluginNetworkVerdict.Denied).reason,
        )
    }

    @Test
    fun aManifestsDeclarationIsWhatGetsEnforced() {
        // The shape the bundled Hue plugin ships: its bridge is on the LAN, and its discovery
        // service is the one internet host it may reach.
        val manifest = Json.decodeFromString(
            PluginManifest.serializer(),
            """{"uuid":"$UUID","name":"Hue","usesPermissions":["LocalNetwork",
               {"name":"Internet","parameters":{"domains":["discovery.meethue.com"]}}]}""",
        )
        val policy = PluginNetworkPolicy(manifest.usesPermissions)
        assertTrue(policy.allows("http://192.168.1.4/api/abc/lights"))
        assertTrue(policy.allows("https://discovery.meethue.com/"))
        assertTrue(!policy.allows("https://telemetry.example.com/collect"))
    }

    @Test
    fun theHostsOwnJsIsUnrestricted() {
        // PKJS is the user's own watchapp, not a plugin: it keeps the reach it has always had.
        assertTrue(PluginNetworkPolicy.unrestricted.allows("https://anything.example.com/"))
        assertTrue(PluginNetworkPolicy.unrestricted.allows("http://192.168.1.4/"))
    }
}
