package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.LibPebbleNotificationAction
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.LibPebbleNotificationAction.ActionType
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.LibPebbleNotificationAction.SemanticAction
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationActionResultTest {
    private data class Case(
        val title: String,
        val semanticAction: SemanticAction,
        val expectedIcon: TimelineIcon,
        val expectedTitle: String,
    )

    private fun action(
        title: String = "Action",
        semanticAction: SemanticAction = SemanticAction.None,
    ) = LibPebbleNotificationAction(
        packageName = "com.example.app",
        title = title,
        semanticAction = semanticAction,
        pendingIntent = null,
        remoteInput = null,
        type = ActionType.Generic,
    )

    @Test
    fun `semantic actions map to result titles and icons`() {
        val cases = listOf(
            Case("Gefällt mir", SemanticAction.ThumbsUp, TimelineIcon.ThumbsUp, "Liked"),
            Case("Dislike", SemanticAction.ThumbsDown, TimelineIcon.GenericConfirmation, "Disliked"),
            Case("Mark as read", SemanticAction.MarkAsRead, TimelineIcon.NotificationGoogleInbox, "Marked as read"),
            Case("Mark as unread", SemanticAction.MarkAsUnread, TimelineIcon.GenericEmail, "Marked as unread"),
            Case("Delete", SemanticAction.Delete, TimelineIcon.ResultDeleted, "Deleted"),
            Case("Archive", SemanticAction.Archive, TimelineIcon.GenericConfirmation, "Archived"),
            Case("Mute", SemanticAction.Mute, TimelineIcon.ResultMute, "Muted"),
            Case("Unmute", SemanticAction.Unmute, TimelineIcon.ResultUnmute, "Unmuted"),
            Case("Call", SemanticAction.Call, TimelineIcon.DuringPhoneCall, "Calling"),
            Case("Reply", SemanticAction.Reply, TimelineIcon.ResultSent, "Replied"),
            Case("Open", SemanticAction.None, TimelineIcon.GenericConfirmation, "Complete"),
        )

        for (case in cases) {
            val result = action(case.title, case.semanticAction).genericActionResult()

            assertTrue(result.success)
            assertEquals(case.expectedIcon, result.icon, "icon for ${case.semanticAction}")
            assertEquals(case.expectedTitle, result.title, "title for ${case.semanticAction}")
        }
    }

    @Test
    fun `like title without semantic action returns liked with thumbs up icon`() {
        val result = action(title = "  Like  ").genericActionResult()

        assertTrue(result.success)
        assertEquals(TimelineIcon.ThumbsUp, result.icon)
        assertEquals("Liked", result.title)
    }
}
