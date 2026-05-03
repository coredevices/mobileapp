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

        private val RESOLVE_TIMEOUT = 16.seconds
        private const val MAX_ATTEMPTS = 4

        /**
         * Backoff delays BEFORE each attempt, in milliseconds. Index 0 is
         * before attempt 1 (initial fire), index N is before attempt N+1.
         *
         *   attempt 1: 0ms baseline (immediate, but with jitter)
         *   attempt 2: 2000ms after failure of 1
         *   attempt 3: 4000ms after failure of 2
         *   attempt 4: 6000ms after failure of 3
         *
         * Each value gets ±[JITTER_MS] of symmetric noise added. The reason
         * we don't fire at exactly 0/2/4/6s: Google's anti-bot heuristics
         * appear to fingerprint request timing patterns, so perfectly
         * regular intervals are themselves a signal. Jitter adds 200ms of
         * naturalness — small enough to not hurt UX, large enough to
         * scramble the period.
         *
         * Even attempt 1 gets jittered (0..200ms) to avoid the "fire
         * immediately on share intent" pattern that's distinctive in
         * server logs.
         */
        private val BACKOFF_MS = longArrayOf(0L, 2_000L, 4_000L, 6_000L)
        private const val JITTER_MS = 200L
    }

    /**
     * Symmetric jitter around a base delay. Returns base + uniform[-J, +J].
     * For BACKOFF_MS[0] = 0L this returns 0..JITTER_MS (clamped non-negative).
     */
    private fun jittered(baseMs: Long): Long {
        // kotlin.random.Random is fine here — we don't need crypto-grade
        // randomness, just unpredictable-enough timing.
        val noise = kotlin.random.Random.nextLong(-JITTER_MS, JITTER_MS + 1)
        val v = baseMs + noise
        return if (v < 0L) 0L else v
    }

    /**
     * If [url] is a Google Maps short URL, attempt to resolve it to its
     * long form. If [url] is anything else, returns it unchanged. On
     * resolution failure (after retries), returns the original short URL.
     *
     * Never throws — wraps failures into a fall-open return.
     *
     * Retry strategy: Google's Firebase Dynamic Links anti-bot heuristic is
     * stochastic — the same headers can yield 200 once and 404/403 the next
     * second. We use four attempts with exponentially-increasing backoff
     * (0, 2s, 4s, 6s) plus per-attempt jitter (±200ms) to spread requests
     * across the suspect rate-limit window without looking like a robotic
     * polling loop. Total worst-case latency is bounded by RESOLVE_TIMEOUT
     * and runs concurrently with PKJS spinup so partial latency is hidden.
     */
    suspend fun resolveIfShortened(url: String): String {
        if (!isShortenedMapsUrl(url)) return url

        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT) {
            for (attempt in 1..MAX_ATTEMPTS) {
                // Pre-attempt wait with jitter. Even the first attempt gets
                // 0..JITTER_MS of jitter so back-to-back shares don't all
                // fire at exactly the same offset from the share intent.
                val waitMs = jittered(BACKOFF_MS[attempt - 1])
                if (waitMs > 0L) {
                    logger.v { "resolve attempt $attempt waiting ${waitMs}ms before fire" }
                    kotlinx.coroutines.delay(waitMs)
                }
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
                // Loop continues; next iteration's pre-attempt wait kicks in.
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
