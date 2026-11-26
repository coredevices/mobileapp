package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationsPluginTest {

    private fun shade(
        sensitive: Boolean = false,
        isOngoing: Boolean = false,
        wantOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        title: CharSequence? = "Alice",
        text: CharSequence? = "Hello",
        bigText: CharSequence? = null,
        subText: CharSequence? = null,
        progress: Int? = null,
        progressMax: Int? = null,
    ) = shadeNotification(
        sensitive = sensitive,
        key = "0|com.example.app|1|null|10",
        packageName = "com.example.app",
        isOngoing = isOngoing,
        wantOngoing = wantOngoing,
        isGroupSummary = isGroupSummary,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        progress = progress,
        progressMax = progressMax,
        postedAt = 1_000L,
    )

    @Test
    fun ongoingNotificationsAreNotInTheShadeList() {
        assertNull(shade(isOngoing = true))
    }

    @Test
    fun ongoingSourceIncludesOnlyOngoingNotifications() {
        val result = shade(isOngoing = true, wantOngoing = true, title = "Turn right", text = "200 m", subText = "ETA 11:18")
        assertEquals("Turn right", result?.title)
        assertEquals("200 m", result?.body)
        assertEquals("ETA 11:18", result?.subText)
        assertNull(shade(isOngoing = false, wantOngoing = true))
        assertNull(shade(isOngoing = true, wantOngoing = true, isGroupSummary = true))
    }

    @Test
    fun subTextBidiIsolatesAreStripped() {
        val result = shade(isOngoing = true, wantOngoing = true, subText = "\u2066ETA 11:18\u2069")
        assertEquals("ETA 11:18", result?.subText)
    }

    @Test
    fun progressIsClampedAndRequiresPositiveMax() {
        val normal = shade(isOngoing = true, wantOngoing = true, progress = 40, progressMax = 100)
        assertEquals(40, normal?.progress)
        assertEquals(100, normal?.progressMax)

        val over = shade(isOngoing = true, wantOngoing = true, progress = 150, progressMax = 100)
        assertEquals(100, over?.progress)
        assertEquals(100, over?.progressMax)

        val negative = shade(isOngoing = true, wantOngoing = true, progress = -5, progressMax = 100)
        assertEquals(0, negative?.progress)
        assertEquals(100, negative?.progressMax)

        val zeroMax = shade(isOngoing = true, wantOngoing = true, progress = 10, progressMax = 0)
        assertNull(zeroMax?.progress)
        assertNull(zeroMax?.progressMax)
    }

    @Test
    fun sensitiveRedactsSubText() {
        val flagged = shade(sensitive = true, isOngoing = true, wantOngoing = true, subText = "123456")
        assertEquals(SENSITIVE_CONTENT, flagged?.subText)

        val byLook = shade(isOngoing = true, wantOngoing = true, title = "Auth", text = "Active", subText = "Your code is 482913")
        assertEquals(SENSITIVE_CONTENT, byLook?.subText)
    }

    @Test
    fun progressShapesFormatsValueBoundsAndPercent() {
        val cases = listOf(
            Triple(0, 100, "0%"),
            Triple(40, 100, "40%"),
            Triple(100, 100, "100%"),
            Triple(50_000_000, 100_000_000, "50%"),
        )
        for ((progress, max, expectedPct) in cases) {
            val shapes = progressShapes(progress, max)
            val numeric = Json.decodeFromJsonElement<NumericValueShape>(shapes.getValue(SourceShapeNames.NUMERIC_VALUE))
            val text = Json.decodeFromJsonElement<ShortTextShape>(shapes.getValue(SourceShapeNames.SHORT_TEXT))
            assertEquals(progress.toDouble(), numeric.value)
            assertEquals(0.0, numeric.min)
            assertEquals(max.toDouble(), numeric.max)
            assertEquals(expectedPct, text.text)
        }
    }

    @Test
    fun subTextAloneIsEnoughToKeepANotification() {
        val result = shade(isOngoing = true, wantOngoing = true, title = null, text = null, subText = "ETA 11:18")
        assertEquals("ETA 11:18", result?.title)
        assertEquals("ETA 11:18", result?.subText)
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
