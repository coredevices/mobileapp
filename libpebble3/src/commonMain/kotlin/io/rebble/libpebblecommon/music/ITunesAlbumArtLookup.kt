package io.rebble.libpebblecommon.music

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class ITunesAlbumArtLookup(private val httpClient: HttpClient) {
    private val logger = Logger.withTag("ITunesAlbumArtLookup")
    private val cacheLock = Mutex()
    private val cache = LinkedHashMap<String, ByteArray?>()

    suspend fun artwork(title: String, artist: String, size: Int): ByteArray? {
        val key = "$size\u0000$title\u0000$artist"
        cacheLock.withLock {
            if (cache.containsKey(key)) return cache[key]
        }
        val result = try {
            withTimeoutOrNull(TIMEOUT) { fetch(title, artist, size) } ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "lookup failed" }
            return null
        }
        cacheLock.withLock {
            cache[key] = result.bytes
            while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        }
        return result.bytes
    }

    private class Result(val bytes: ByteArray?)

    // null: retry later. Result(null): this track has no art.
    private suspend fun fetch(title: String, artist: String, size: Int): Result? {
        val response = httpClient.get(SEARCH_URL) {
            parameter("term", searchTerm(title, artist))
            parameter("media", "music")
            parameter("entity", "song")
            parameter("limit", SEARCH_LIMIT)
        }
        if (!response.status.isSuccess()) {
            logger.w { "search failed: ${response.status}" }
            return null
        }
        val artworkUrl = pickArtworkUrl(response.bodyAsText(), title, artist)
            ?: return Result(null).also { logger.d { "no matching search result" } }
        val image = httpClient.get(resizeArtworkUrl(artworkUrl, size))
        if (!image.status.isSuccess()) {
            logger.w { "artwork download failed: ${image.status}" }
            return null
        }
        return Result(image.bodyAsBytes())
    }

    companion object {
        private const val SEARCH_URL = "https://itunes.apple.com/search"
        private const val SEARCH_LIMIT = 10
        private const val CACHE_SIZE = 16
        private val TIMEOUT = 15.seconds
        private val json = Json { ignoreUnknownKeys = true }

        // The watch may cut names mid-character (U+FFFD).
        internal fun searchTerm(title: String, artist: String): String =
            "${artist.substringBefore('�')} ${title.substringBefore('�')}".trim()

        // Exact title first; a prefix match is for titles the watch truncated.
        internal fun pickArtworkUrl(responseJson: String, title: String, artist: String): String? {
            val response = try {
                json.decodeFromString<SearchResponse>(responseJson)
            } catch (e: SerializationException) {
                return null
            }
            val candidates = response.results.filter {
                it.artworkUrl100 != null &&
                    matchesIgnoreCase(it.trackName, title) && artistMatches(it.artistName, artist)
            }
            return (candidates.firstOrNull { it.trackName.equals(title, ignoreCase = true) }
                ?: candidates.firstOrNull())?.artworkUrl100
        }

        // Artwork URLs end in e.g. "/100x100bb.jpg"; the size in it is selectable.
        internal fun resizeArtworkUrl(url: String, size: Int): String =
            url.replace(SIZE_SUFFIX, "/${size}x${size}bb.")

        private val SIZE_SUFFIX = Regex("""/\d+x\d+bb\.""")

        private fun matchesIgnoreCase(full: String?, truncated: String): Boolean =
            truncated.isNotBlank() &&
                matchesTruncated(full?.lowercase(), truncated.lowercase())

        private fun artistMatches(full: String?, artist: String): Boolean {
            if (artist.isBlank()) return true
            val intact = artist.substringBefore('�').lowercase()
            val candidate = full?.lowercase() ?: return false
            return candidate.startsWith(intact) || intact.startsWith(candidate)
        }
    }

    @Serializable
    internal data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    internal data class SearchResult(
        val trackName: String? = null,
        val artistName: String? = null,
        val artworkUrl100: String? = null,
    )
}
