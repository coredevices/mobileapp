@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.integrations

import coredevices.mcp.SessionContext
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ItemSourceTest {

    @Test
    fun itemSourceCarriesRecordingTimeBaseAndToolCallId() {
        val now = Clock.System.now()
        val context = SessionContext(
            timeBase = now,
            userMessageText = CompletableDeferred("hi"),
            recordingFirestoreId = "rec-1",
            toolCallId = "call-1",
        )

        val source = context.itemSource()

        assertEquals("rec-1", source.recordingFirestoreId)
        assertEquals(now, source.createdAt)
        assertEquals("call-1", source.toolCallId)
    }
}
