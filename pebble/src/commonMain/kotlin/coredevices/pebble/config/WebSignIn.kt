package coredevices.pebble.config

import PlatformUiContext
import kotlinx.serialization.Serializable

/** What a config page asks for when it calls `Pebble.webSignIn`. */
@Serializable
data class WebSignInRequest(
    /** Where the sign-in flow starts. */
    val url: String,
    /** The cookie names that must all be present before sign-in counts as done. */
    val cookies: List<String>,
    /** The domain those cookies belong to. */
    val domain: String,
)

/**
 * Shows [request]'s url full-screen in a throwaway WebView the page can neither see nor script,
 * cleared before and after, and returns the requested cookies once they all exist for the domain,
 * or null if the user backs out.
 */
internal expect suspend fun performWebSignIn(
    uiContext: PlatformUiContext,
    request: WebSignInRequest,
): Map<String, String>?

/**
 * Whether this device can run the sign-in WebView in an isolated cookie jar. When false the feature
 * is disabled rather than falling back to a shared jar (Android needs WebView multi-profile support).
 */
internal expect fun webSignInSupported(): Boolean

/** Standard cookie domain-match: a cookie for `example.com` applies to `www.example.com`. */
internal fun domainMatches(host: String, cookieDomain: String): Boolean {
    val cookie = cookieDomain.removePrefix(".").lowercase()
    val target = host.removePrefix(".").lowercase()
    return target == cookie || target.endsWith(".$cookie")
}
