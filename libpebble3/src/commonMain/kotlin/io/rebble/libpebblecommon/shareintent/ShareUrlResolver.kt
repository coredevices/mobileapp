package io.rebble.libpebblecommon.shareintent

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves Google Maps short URLs (`maps.app.goo.gl`, `goo.gl/maps`) to their
 * full long-form URLs.
 *
 * Owns a dedicated [HttpClient] instance rather than using libpebble3's shared
 * singleton client. Reasons:
 *
 *   1. **Isolation**: Google's anti-bot heuristics occasionally make the
 *      resolver retry, hold connections longer than usual, or hit timeouts.
 *      Sharing the singleton client means our retry pressure can starve
 *      unrelated consumers (FirmwareDownloader, Locker, watchapp PBW
 *      downloads, etc.) of connection-pool slots.
 *   2. **Engine-level timeouts**: the singleton client has no HttpTimeout
 *      plugin installed, so requests can hang indefinitely on the underlying
 *      OkHttp engine. We need bounded socket/connect/request timeouts at
 *      the engine level so a stuck Google response can't hold a connection
 *      forever.
 *   3. **Targeted lifecycle**: when libpebble3 is shut down, we close our
 *      client deterministically rather than relying on the shared client's
 *      lifecycle.
 *
 * See the resolution path docs in [resolveIfShortened] for the why-not-PKJS-XHR
 * background.
 */
class ShareUrlResolver internal constructor(
    private val httpClient: HttpClient,
) {
    /**
     * Production constructor. Builds an HttpClient configured for the
     * resolver's specific use case:
     *   - Engine-level timeouts (HttpTimeout plugin) so stuck connections
     *     don't pin pool slots.
     *   - Default redirect-following (Ktor follows up to 20 redirects by
     *     default; Google short URLs are typically 1-2 hops).
     *
     * Tests can inject a custom HttpClient via the internal constructor.
     */
    constructor() : this(httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis  = 5_000  // total request lifetime
            connectTimeoutMillis  = 2_000  // TCP connect
            socketTimeoutMillis   = 3_000  // between bytes
        }
    })
    companion object {
        private val logger = Logger.withTag(ShareUrlResolver::class.simpleName!!)

        /**
         * Hosts that emit Google Maps short URLs. The resolver will only
         * follow redirects when the *input* URL is on this list. Once the
         * redirect chain leaves these hosts, we accept whatever final URL
         * the HTTP layer reports.
         */
        private val SHORT_URL_HOSTS = setOf(
            "maps.app.goo.gl",
            "goo.gl",  // for the older /maps/<id> form
        )

        /**
         * Stable mobile-Chrome User-Agent. Not version-spoofing — picked to
         * be representative rather than chasing the latest Chrome release.
         * Updated rarely.
         */
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val RESOLVE_TIMEOUT = 6.seconds
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 300L
    }

    /**
     * If [url] is a Google Maps short URL, attempt to resolve it to its
     * long form. If [url] is anything else, returns it unchanged. On
     * resolution failure (after retries), returns the original short URL.
     *
     * Never throws — wraps failures into a fall-open return.
     *
     * Retry strategy: Google's Firebase Dynamic Links anti-bot heuristic is
     * stochastic — the same headers can yield 200 once and 403 the next
     * second. A single retry roughly doubles our success rate at minimal
     * cost. Total worst-case latency is bounded by RESOLVE_TIMEOUT and
     * runs concurrently with PKJS spinup so usually invisible.
     */
    suspend fun resolveIfShortened(url: String): String {
        if (!isShortenedMapsUrl(url)) return url

        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT) {
            for (attempt in 1..MAX_ATTEMPTS) {
                val r = try {
                    doResolve(url)
                } catch (e: Exception) {
                    logger.w(e) { "resolve attempt $attempt failed for $url" }
                    null
                }
                if (r != null) {
                    if (attempt > 1) logger.i { "resolve succeeded on attempt $attempt" }
                    return@withTimeoutOrNull r
                }
                if (attempt < MAX_ATTEMPTS) {
                    // Brief backoff before retry. Doesn't need to be long —
                    // Google's anti-bot decision seems request-local rather
                    // than IP-rate-based, so even ~300ms is enough to land
                    // a different decision tree.
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                }
            }
            null
        }
        return if (resolved != null) {
            logger.i { "resolved $url -> $resolved" }
            resolved
        } else {
            logger.w { "resolve gave no result for $url after $MAX_ATTEMPTS attempts, falling open" }
            url
        }
    }

    /**
     * Public for testing / introspection. Returns true if the URL's host is
     * one of the whitelisted Google Maps short-URL hosts.
     */
    fun isShortenedMapsUrl(url: String): Boolean {
        val host = try { Url(url).host } catch (_: Exception) { return false }
        return host in SHORT_URL_HOSTS
    }

    private suspend fun doResolve(url: String): String? {
        val startNs = kotlin.time.TimeSource.Monotonic.markNow()

        // Ktor's HttpClient follows redirects by default. After the call, the
        // response.call.request.url is the *final* URL (post-redirects).
        // That's the resolution we want.
        //
        // Outer timeout is enforced by withTimeoutOrNull in resolveIfShortened;
        // we don't install the HttpTimeout plugin on the client here because
        // it's a shared singleton with other use cases.
        val response: HttpResponse = httpClient.get(url) {
            header("User-Agent", MOBILE_UA)
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.9")
        }

        val statusCode = response.status.value
        val elapsedMs = startNs.elapsedNow().inWholeMilliseconds
        val finalUrl = response.call.request.url.toString()

        logger.d { "resolve $url → status=$statusCode finalUrl=$finalUrl elapsed=${elapsedMs}ms" }

        if (!response.status.isSuccess() && statusCode !in 300..399) {
            return null
        }

        // Sanity check: if the "final" URL is the same as the input we got
        // no useful redirect. Treat as failure so caller falls open.
        if (finalUrl == url) {
            return null
        }
        return finalUrl
    }
}
