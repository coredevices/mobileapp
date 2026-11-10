package coredevices.pebble.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigPageUrlFilterTest {
    private val filter = Regex(configPageUrlFilter("^https?://", "allowed.com"))

    @Test
    fun matchesTheDomainAndItsSubdomains() {
        assertTrue(filter.containsMatchIn("https://allowed.com/"))
        assertTrue(filter.containsMatchIn("http://api.allowed.com/v1?x=1"))
        assertTrue(filter.containsMatchIn("https://allowed.com:8443/"))
    }

    @Test
    fun userinfoCantSmuggleInAnotherHost() {
        assertFalse(filter.containsMatchIn("https://allowed.com:p@evil.com/"))
        assertFalse(filter.containsMatchIn("https://allowed.com@evil.com/"))
        assertFalse(filter.containsMatchIn("https://x.allowed.com:1@evil.com/"))
    }

    @Test
    fun lookalikeHostsDontMatch() {
        assertFalse(filter.containsMatchIn("https://evil-allowed.com/"))
        assertFalse(filter.containsMatchIn("https://allowed.com.evil.com/"))
        assertFalse(filter.containsMatchIn("https://evil.com/?u=https://allowed.com/"))
    }
}
