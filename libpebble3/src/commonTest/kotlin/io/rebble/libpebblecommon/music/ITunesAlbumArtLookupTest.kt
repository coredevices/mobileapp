package io.rebble.libpebblecommon.music

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ITunesAlbumArtLookupTest {
    // Under runTest's virtual time the lookup's timeout fires before the mock engine answers.
    private fun runRealTime(block: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    private val searchJson = """
        {"resultCount":3,"results":[
          {"trackName":"One More Time (Live)","artistName":"Daft Punk",
           "artworkUrl100":"https://is1.mzstatic.com/a/live/100x100bb.jpg"},
          {"trackName":"One More Time","artistName":"Daft Punk",
           "artworkUrl100":"https://is1.mzstatic.com/a/discovery/100x100bb.jpg","extra":1},
          {"trackName":"Get Lucky","artistName":"Daft Punk & Pharrell Williams",
           "artworkUrl100":"https://is1.mzstatic.com/a/ram/100x100bb.jpg"}
        ]}
    """.trimIndent()

    @Test
    fun prefersExactTitleOverPrefix() {
        // The live version also starts with the title, and comes first
        assertEquals(
            "https://is1.mzstatic.com/a/discovery/100x100bb.jpg",
            ITunesAlbumArtLookup.pickArtworkUrl(searchJson, "One More Time", "Daft Punk"),
        )
        assertEquals(
            "https://is1.mzstatic.com/a/ram/100x100bb.jpg",
            ITunesAlbumArtLookup.pickArtworkUrl(searchJson, "get lucky", "Daft Punk"),
        )
    }

    @Test
    fun noMatchMeansNoArt() {
        assertNull(ITunesAlbumArtLookup.pickArtworkUrl(searchJson, "Harder, Better", "Daft Punk"))
        assertNull(ITunesAlbumArtLookup.pickArtworkUrl(searchJson, "One More Time", "Someone Else"))
        assertNull(ITunesAlbumArtLookup.pickArtworkUrl(searchJson, "", "Daft Punk"))
        assertNull(ITunesAlbumArtLookup.pickArtworkUrl("not json", "One More Time", "Daft Punk"))
    }

    @Test
    fun toleratesWatchTruncation() {
        // The watch cut the title mid-character, decoding as U+FFFD
        assertEquals(
            "https://is1.mzstatic.com/a/live/100x100bb.jpg",
            ITunesAlbumArtLookup.pickArtworkUrl(searchJson, "One More T�", "Daft Punk"),
        )
        assertEquals("Daft Punk One More T", ITunesAlbumArtLookup.searchTerm("One More T�", "Daft Punk"))
    }

    @Test
    fun resizesArtworkUrl() {
        assertEquals(
            "https://is1.mzstatic.com/a/x/144x144bb.jpg",
            ITunesAlbumArtLookup.resizeArtworkUrl("https://is1.mzstatic.com/a/x/100x100bb.jpg", 144),
        )
    }

    @Test
    fun searchesDownloadsAndCaches() = runRealTime {
        val requests = mutableListOf<String>()
        val art = byteArrayOf(1, 2, 3)
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if (request.url.host == "itunes.apple.com") {
                respond(searchJson)
            } else {
                respond(art)
            }
        })
        val lookup = ITunesAlbumArtLookup(client)

        assertContentEquals(art, lookup.artwork("Get Lucky", "Daft Punk", 144))
        assertEquals(2, requests.size)
        assertEquals("https://is1.mzstatic.com/a/ram/144x144bb.jpg", requests[1])

        assertContentEquals(art, lookup.artwork("Get Lucky", "Daft Punk", 144))
        assertEquals(2, requests.size, "second lookup should come from the cache")
    }

    @Test
    fun cachesNoMatchButNotFailures() = runRealTime {
        var searches = 0
        var failSearch = true
        val client = HttpClient(MockEngine { _ ->
            searches++
            if (failSearch) respondError(HttpStatusCode.ServiceUnavailable) else respond(searchJson)
        })
        val lookup = ITunesAlbumArtLookup(client)

        assertNull(lookup.artwork("Unknown Song", "Daft Punk", 144))
        failSearch = false
        assertNull(lookup.artwork("Unknown Song", "Daft Punk", 144))
        assertEquals(2, searches, "a failed search isn't cached")

        assertNull(lookup.artwork("Unknown Song", "Daft Punk", 144))
        assertEquals(2, searches, "a search without a match is cached")
    }
}
