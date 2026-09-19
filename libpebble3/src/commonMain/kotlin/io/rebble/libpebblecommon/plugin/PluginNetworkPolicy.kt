package io.rebble.libpebblecommon.plugin

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * What a plugin is allowed to reach over the network, derived from the `usesPermissions` it
 * declared. Enforced in [io.rebble.libpebblecommon.js.XMLHTTPRequestManager], which is every
 * plugin's only way out — the engines' own `fetch`/`WebSocket` are blocked.
 *
 * A host is private or public, and each answers to its own permission: a device on the LAN needs
 * `LocalNetwork`, anything else needs `Internet`. `Internet` declared with a `domains` parameter
 * grants those hosts and their subdomains rather than the web at large.
 *
 * This is the whole of what `usesPermissions` currently means: network is the only capability a
 * plugin is granted by the host. A built-in plugin needs none of it — it runs in the app and
 * reads what the app already has.
 */
class PluginNetworkPolicy(permissions: List<PluginPermission>) {

    private val localNetwork = permissions.any { it.name == PluginPermissions.LOCAL_NETWORK }
    private val internet = permissions.firstOrNull { it.name == PluginPermissions.INTERNET }
    private val domains = internet?.parameters?.get(PluginPermissions.PARAM_DOMAINS)
        ?.map { it.lowercase().removePrefix(".") }
        .orEmpty()

    fun check(url: String): PluginNetworkVerdict {
        val host = hostOf(url)
            ?: return PluginNetworkVerdict.Denied("'$url' is not a URL this plugin can request")
        val reach = reachOf(host)
            ?: return PluginNetworkVerdict.Denied("'$host' is not a host this plugin can request")
        if (reach == Reach.LocalNetwork) {
            return if (localNetwork) PluginNetworkVerdict.Allowed
            else PluginNetworkVerdict.Denied("$host is on the local network, which this plugin did not ask for")
        }
        if (internet == null) {
            return PluginNetworkVerdict.Denied("this plugin did not ask for internet access")
        }
        // No domains listed is the un-narrowed form: the plugin declared the whole web, which a
        // reviewer can see in its manifest.
        if (domains.isEmpty() || domains.any { host == it || host.endsWith(".$it") }) {
            return PluginNetworkVerdict.Allowed
        }
        return PluginNetworkVerdict.Denied("$host is not one of the domains this plugin declared")
    }

    companion object {
        /** What PKJS and the app's own JS get: these are the user's own apps, not plugins. */
        val unrestricted = PluginNetworkPolicy(
            listOf(
                PluginPermission(PluginPermissions.LOCAL_NETWORK),
                PluginPermission(PluginPermissions.INTERNET),
            )
        )

        /**
         * The host of an absolute http(s) URL. Ktor does the parsing — the same library that
         * makes the request, so what is checked here is what is dialled. Null for anything
         * else: a plugin has no origin to resolve a relative URL against, and `file:` /
         * `content:` are not its to read.
         */
        private fun hostOf(url: String): String? {
            val parsed = runCatching { Url(url) }.getOrNull() ?: return null
            if (parsed.protocol != URLProtocol.HTTP && parsed.protocol != URLProtocol.HTTPS) {
                return null
            }
            return parsed.host
                .removeSurrounding("[", "]")   // ktor keeps an IPv6 literal's brackets
                .lowercase()
                .trimEnd('.')
                .ifEmpty { null }
        }

        /**
         * Which side of the permission split a host falls on, or null for one we will not dial
         * at all. A resolver accepts an IPv4 address written several ways — `2130706433`,
         * `0x7f000001`, `127.1`, `0177.0.0.1` — and each is a way to name a LAN device without
         * looking like one, so anything numeric that isn't a canonical dotted quad is refused
         * rather than guessed at.
         */
        private fun reachOf(host: String): Reach? {
            if (host.contains(':')) return reachOfIpv6(host)
            if (host == "localhost" || host.endsWith(".localhost")) return Reach.LocalNetwork
            if (host.endsWith(".local") || host.endsWith(".home.arpa") ||
                host.endsWith(".internal")
            ) {
                return Reach.LocalNetwork
            }
            val labels = host.split('.')
            // A DNS name's last label is never all digits, so anything that ends in one is an
            // address rather than a name — and must be written as four plain octets.
            val looksNumeric = labels.last().let { it.isNotEmpty() && it.all(Char::isDigit) } ||
                host.startsWith("0x", ignoreCase = true)
            if (!looksNumeric) return Reach.Internet
            return reachOfIpv4(quadOf(labels) ?: return null)
        }

        /** Four plain octets, or null for any other spelling of an address. */
        private fun quadOf(labels: List<String>): IntArray? {
            if (labels.size != 4) return null
            val bytes = IntArray(4)
            labels.forEachIndexed { index, label ->
                // A leading zero means octal to a resolver but decimal here, so refuse it.
                if (label.isEmpty() || (label.length > 1 && label[0] == '0')) return null
                bytes[index] = label.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            }
            return bytes
        }

        private fun reachOfIpv4(bytes: IntArray): Reach = when {
            bytes[0] == 10 || bytes[0] == 127 || bytes[0] == 0 -> Reach.LocalNetwork
            bytes[0] == 192 && bytes[1] == 168 -> Reach.LocalNetwork
            bytes[0] == 172 && bytes[1] in 16..31 -> Reach.LocalNetwork
            bytes[0] == 169 && bytes[1] == 254 -> Reach.LocalNetwork     // link-local
            bytes[0] == 100 && bytes[1] in 64..127 -> Reach.LocalNetwork // carrier NAT
            else -> Reach.Internet
        }

        private fun reachOfIpv6(host: String): Reach? {
            // An IPv4-mapped address routes to the v4 address, so it is that address' reach.
            val mapped = host.substringAfterLast("::ffff:", missingDelimiterValue = "")
            if (mapped.isNotEmpty()) {
                if (mapped.contains('.')) {
                    return reachOfIpv4(quadOf(mapped.split('.')) ?: return null)
                }
                // The same thing in hex: ::ffff:c0a8:101
                val words = mapped.split(':')
                if (words.size != 2) return null
                val high = words[0].toIntOrNull(16) ?: return null
                val low = words[1].toIntOrNull(16) ?: return null
                if (high !in 0..0xFFFF || low !in 0..0xFFFF) return null
                return reachOfIpv4(
                    intArrayOf(high shr 8, high and 0xFF, low shr 8, low and 0xFF)
                )
            }
            if (host == "::1" || host == "::") return Reach.LocalNetwork
            val prefix = host.substringBefore(':').lowercase()
            // Unique-local (fc00::/7) and link-local (fe80::/10).
            if (prefix.length == 4 && (prefix.startsWith("fc") || prefix.startsWith("fd"))) {
                return Reach.LocalNetwork
            }
            if (prefix.startsWith("fe8") || prefix.startsWith("fe9") ||
                prefix.startsWith("fea") || prefix.startsWith("feb")
            ) {
                return Reach.LocalNetwork
            }
            return Reach.Internet
        }
    }
}

/** Which permission a host answers to. */
private enum class Reach { LocalNetwork, Internet }

sealed interface PluginNetworkVerdict {
    object Allowed : PluginNetworkVerdict
    data class Denied(val reason: String) : PluginNetworkVerdict
}
