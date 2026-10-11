package coredevices.pebble.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSignInDomainMatchTest {
    @Test
    fun cookieAppliesToExactHostAndSubdomains() {
        assertTrue(domainMatches("example.com", "example.com"))
        assertTrue(domainMatches("www.example.com", "example.com"))
        assertTrue(domainMatches("example.com", ".example.com"))
    }

    @Test
    fun cookieDoesNotApplyToOtherDomains() {
        assertFalse(domainMatches("example.com", "evil.com"))
        assertFalse(domainMatches("evil-example.com", "example.com"))
        assertFalse(domainMatches("notexample.com", "example.com"))
        // A cookie scoped to a subdomain must not leak up to the parent host.
        assertFalse(domainMatches("example.com", "auth.example.com"))
    }
}
