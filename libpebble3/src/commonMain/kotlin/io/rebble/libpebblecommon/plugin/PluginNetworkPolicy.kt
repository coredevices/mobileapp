package io.rebble.libpebblecommon.plugin

import io.ktor.http.Url
import io.rebble.libpebblecommon.permissions.internetKey

/** Maps a URL to the permission needed to reach it: `LocalNetwork` for the LAN, else `Internet/<domain>`. */
object PluginNetworkPolicy {
    /** Null if [url] can't be requested at all. */
    fun permissionFor(url: String): String? {
        val host = hostOf(url) ?: return null
        return when (reachOf(host)) {
            Reach.LocalNetwork -> PluginPermissions.LOCAL_NETWORK
            Reach.Internet -> internetKey(host)
            null -> null
        }
    }

    private val requestableSchemes = listOf("http://", "https://", "ws://", "wss://")

    fun isRequestable(url: String): Boolean = requestableSchemes.any { url.startsWith(it, ignoreCase = true) }

    /** Parsed with Ktor, which also makes the request, so the checked host is the one dialled. */
    private fun hostOf(url: String): String? {
        // Ktor resolves a URL without a scheme against http://localhost.
        if (!isRequestable(url)) return null
        val parsed = runCatching { Url(url) }.getOrNull() ?: return null
        return parsed.host
            .removeSurrounding("[", "]")   // ktor keeps an IPv6 literal's brackets
            .lowercase()
            .trimEnd('.')
            .ifEmpty { null }
    }

    /** Null for a host we won't dial: numeric hosts must be a plain dotted quad (not `0x7f000001`, `127.1`, ...). */
    private fun reachOf(host: String): Reach? {
        if (host.contains(':')) return reachOfIpv6(host)
        if (host == "localhost" || host.endsWith(".localhost")) return Reach.LocalNetwork
        if (host.endsWith(".local") || host.endsWith(".home.arpa") ||
            host.endsWith(".internal")
        ) {
            return Reach.LocalNetwork
        }
        val labels = host.split('.')
        // A DNS name's last label is never all digits.
        val looksNumeric = labels.last().let { it.isNotEmpty() && it.all(Char::isDigit) } ||
            host.startsWith("0x", ignoreCase = true)
        if (!looksNumeric) return Reach.Internet
        return reachOfIpv4(quadOf(labels) ?: return null)
    }

    private fun quadOf(labels: List<String>): IntArray? {
        if (labels.size != 4) return null
        val bytes = IntArray(4)
        labels.forEachIndexed { index, label ->
            // Leading zero means octal to a resolver.
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
        // IPv4-mapped
        val mapped = host.substringAfterLast("::ffff:", missingDelimiterValue = "")
        if (mapped.isNotEmpty()) {
            if (mapped.contains('.')) {
                return reachOfIpv4(quadOf(mapped.split('.')) ?: return null)
            }
            // ::ffff:c0a8:101
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

private enum class Reach { LocalNetwork, Internet }

sealed interface PluginNetworkVerdict {
    object Allowed : PluginNetworkVerdict
    data class Denied(val reason: String) : PluginNetworkVerdict
}
