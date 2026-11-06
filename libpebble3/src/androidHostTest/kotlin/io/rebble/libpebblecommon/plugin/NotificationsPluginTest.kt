package io.rebble.libpebblecommon.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationsPluginTest {

    private fun shade(
        sensitive: Boolean = false,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        title: CharSequence? = "Alice",
        text: CharSequence? = "Hello",
        bigText: CharSequence? = null,
    ) = shadeNotification(
        sensitive = sensitive,
        key = "0|com.example.app|1|null|10",
        packageName = "com.example.app",
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
        title = title,
        text = text,
        bigText = bigText,
        postedAt = 1_000L,
    )

    @Test
    fun ongoingNotificationsAreNotInTheShadeList() {
        assertNull(shade(isOngoing = true))
    }

    @Test
    fun groupSummariesAreDropped() {
        assertNull(shade(isGroupSummary = true))
    }

    @Test
    fun emptyNotificationsAreDropped() {
        assertNull(shade(title = null, text = null))
        assertNull(shade(title = "  ", text = ""))
    }

    @Test
    fun bigTextWinsOverText() {
        assertEquals("The whole message", shade(bigText = "The whole message")?.body)
    }

    @Test
    fun aTitlelessNotificationIsNamedByItsBody() {
        val result = shade(title = null, text = "Your parcel has arrived")
        assertEquals("Your parcel has arrived", result?.title)
        assertEquals("", result?.body)
    }

    @Test
    fun bidiIsolatesAreStripped() {
        assertEquals("Alice", shade(title = "\u2066Alice\u2069")?.title)
    }

    @Test
    fun whatAndroidFlagsAsSensitiveIsReplaced() {
        val result = shade(sensitive = true, title = "56439", text = "Your Fidelity verification code is 155319.")
        assertEquals(SENSITIVE_CONTENT, result?.body)
        assertEquals(SENSITIVE_CONTENT, result?.title)
    }

    @Test
    fun aFlaggedNotificationKeepsATitleWithoutACode() {
        val result = shade(sensitive = true, title = "Bank", text = "Your code is in the app")
        assertEquals("Bank", result?.title)
        assertEquals(SENSITIVE_CONTENT, result?.body)
    }

    @Test
    fun aCodeAndroidDidntFlagIsCaughtByItsLook() {
        assertEquals(SENSITIVE_CONTENT, shade(title = "Bank", text = "Your verification code is 482913")?.body)
        assertEquals(SENSITIVE_CONTENT, shade(title = "Acme", text = "Use 4829-130 to sign in")?.body)
        val google = shade(title = "G-482913 is your Google verification code", text = "Don't share it")
        assertEquals(SENSITIVE_CONTENT, google?.title)
        assertEquals(SENSITIVE_CONTENT, google?.body)
    }

    @Test
    fun aNumberWithoutCodeWordsIsLeftAlone() {
        for (text in listOf("Your order #48291 has shipped", "Meeting moved to 2027", "You paid $1250 to Sam", "Delivery between 1400 and 1600")) {
            assertEquals(text, shade(text = text)?.body, text)
        }
    }

    @Test
    fun codeWordsWithoutACodeAreLeftAlone() {
        assertEquals("Sign in to see your new messages", shade(text = "Sign in to see your new messages")?.body)
    }
}
