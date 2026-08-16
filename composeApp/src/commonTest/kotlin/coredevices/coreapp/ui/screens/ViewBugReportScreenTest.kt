package coredevices.coreapp.ui.screens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewBugReportScreenTest {
    @Test
    fun bugReportPagesStayInWebview() {
        assertFalse(
            shouldOpenBugReportLinkExternally(
                "https://dash.repebble.com/bugreports/intercom?id=123&email=a%40b.com&hash=abc"
            )
        )
        assertFalse(
            shouldOpenBugReportLinkExternally(
                "https://dash.repebble.com/bugreports/ticket?id=123&email=a%40b.com&hash=abc"
            )
        )
    }

    @Test
    fun atlasEmbedStaysInWebview() {
        assertFalse(shouldOpenBugReportLinkExternally("https://embed.atlas.so/index.html?appId=x"))
        assertFalse(shouldOpenBugReportLinkExternally("https://api.atlas.so/v1/whatever"))
    }

    @Test
    fun messageLinksOpenExternally() {
        assertTrue(shouldOpenBugReportLinkExternally("https://linear.app/core-dev/issue/8ec5b143"))
        assertTrue(shouldOpenBugReportLinkExternally("http://example.com/page"))
    }

    @Test
    fun dashPagesOutsideBugreportsOpenExternally() {
        assertTrue(
            shouldOpenBugReportLinkExternally(
                "https://dash.repebble.com/log-viewer?url=https%3A%2F%2Fdash.repebble.com%2Fapi%2Fattachments"
            )
        )
        assertTrue(
            shouldOpenBugReportLinkExternally("https://dash.repebble.com/api/attachments/bug-attachments/x.png")
        )
    }

    @Test
    fun mailtoOpensExternally() {
        assertTrue(shouldOpenBugReportLinkExternally("mailto:support@repebble.com"))
    }

    @Test
    fun nonWebSchemesStayInWebview() {
        assertFalse(shouldOpenBugReportLinkExternally("about:blank"))
        assertFalse(shouldOpenBugReportLinkExternally(""))
    }

    @Test
    fun lookalikeHostsOpenExternally() {
        assertTrue(shouldOpenBugReportLinkExternally("https://evilatlas.so/phish"))
        assertTrue(shouldOpenBugReportLinkExternally("https://dash.repebble.com.evil.com/bugreports/x"))
    }
}
