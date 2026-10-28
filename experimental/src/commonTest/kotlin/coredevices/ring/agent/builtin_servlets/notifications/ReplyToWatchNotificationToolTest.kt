package coredevices.ring.agent.builtin_servlets.notifications

import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import io.rebble.libpebblecommon.connection.DisplayedNotificationReplyResult
import io.rebble.libpebblecommon.connection.NotificationReplies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class ReplyToWatchNotificationToolTest {

    private class FakeNotificationReplies(
        private val result: DisplayedNotificationReplyResult,
    ) : NotificationReplies {
        var sentText: String? = null
        var sentAt: Instant? = null

        override suspend fun replyToDisplayedNotification(
            text: String,
            at: Instant
        ): DisplayedNotificationReplyResult {
            sentText = text
            sentAt = at
            return result
        }
    }

    private val spokenAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun context(timeBase: Instant? = spokenAt) = SessionContext(
        timeBase = timeBase,
        userMessageText = CompletableDeferred("reply on my way"),
    )

    @Test
    fun repliesWithTheTextAtTheTimeTheUserSpoke() = runTest {
        val replies = FakeNotificationReplies(DisplayedNotificationReplyResult.Sent(title = "Alice"))

        val result = ReplyToWatchNotificationTool(replies).call("""{"text": " on my way "}""", context())

        assertEquals("on my way", replies.sentText)
        assertEquals(spokenAt, replies.sentAt)
        val sent = assertIs<SemanticResult.MessageSent>(result.semanticResult)
        assertEquals("Alice", sent.recipientName)
        assertEquals("on my way", sent.text)
    }

    @Test
    fun eachUnsentResultIsADistinctNonRecoverableFailure() = runTest {
        val unsent = listOf(
            DisplayedNotificationReplyResult.NoNotificationDisplayed,
            DisplayedNotificationReplyResult.NotReplyable,
            DisplayedNotificationReplyResult.Unsupported,
            DisplayedNotificationReplyResult.Failed,
        )

        val messages = unsent.map {
            val result = ReplyToWatchNotificationTool(FakeNotificationReplies(it))
                .call("""{"text": "ok"}""", context())
            val failure = assertIs<SemanticResult.GenericFailure>(result.semanticResult)
            assertFalse(failure.llmRecoverable, "$it should not be retried by the LLM")
            failure.userErrorMessage
        }

        assertEquals(unsent.size, messages.toSet().size, "each failure needs its own message: $messages")
    }

    @Test
    fun blankTextIsNotSent() = runTest {
        val replies = FakeNotificationReplies(DisplayedNotificationReplyResult.Sent(title = "Alice"))

        val result = ReplyToWatchNotificationTool(replies).call("""{"text": "  "}""", context())

        assertNull(replies.sentText)
        assertIs<SemanticResult.GenericFailure>(result.semanticResult)
    }
}
