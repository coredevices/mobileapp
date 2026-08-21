package coredevices.mcp.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationNameTest {
    @Test
    fun acceptsNamesThatSurviveTheCompositeRoundTrip() {
        assertTrue(isValidIntegrationName("plexmcp"))
        assertTrue(isValidIntegrationName("plex-mcp"))
        assertTrue(isValidIntegrationName("plex_mcp"))
    }

    @Test
    fun rejectsDotsWhichCactusSplitsOn() {
        // "plex.mcp" + "." + "get_libraries" splits back into "plex" and the rest.
        assertFalse(isValidIntegrationName("plex.mcp"))
        assertFalse(isValidIntegrationName("plex-mcp.germany.vertesi.com"))
    }

    @Test
    fun rejectsCharactersNenyaWouldSanitize() {
        assertFalse(isValidIntegrationName("plex mcp"))
        assertFalse(isValidIntegrationName("plex/mcp"))
    }

    @Test
    fun rejectsASecondNenyaSeparator() {
        assertFalse(isValidIntegrationName("plex__mcp"))
    }

    @Test
    fun rejectsEmptyName() {
        assertFalse(isValidIntegrationName(""))
    }

    @Test
    fun rejectsNamesThatCrowdOutTheToolHalf() {
        // sanitizeToolName truncates "$name__$tool" at 64 characters, which silently
        // rewrites the tool half rather than the name.
        assertTrue(isValidIntegrationName("a".repeat(32)))
        assertFalse(isValidIntegrationName("a".repeat(33)))
    }
}
