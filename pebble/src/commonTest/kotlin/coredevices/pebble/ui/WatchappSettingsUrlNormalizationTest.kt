package coredevices.pebble.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchappSettingsUrlNormalizationTest {
    @Test
    fun rewritesLegacyRawGitUrlsToRawGitHack() {
        val rawGitUrl = "https://cdn.rawgit.com/groyoh/minimalin/ffd0da5fb45f0722dee6e59eb4b05fa63ca82136/config/index.html"

        assertEquals(
            "https://raw.githack.com/groyoh/minimalin/ffd0da5fb45f0722dee6e59eb4b05fa63ca82136/config/index.html",
            normalizeWatchappSettingsUrl(rawGitUrl)
        )
    }

    @Test
    fun keepsNonRawGitUrlsAsIs() {
        val url = "https://example.com/config/index.html?foo=bar"

        assertEquals(url, normalizeWatchappSettingsUrl(url))
    }
}
